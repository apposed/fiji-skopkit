/*-
 * #%L
 * scikit-ops in Fiji: every op, as its own SciJava command.
 * %%
 * Copyright (C) 2026 scikit-ops developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.apposed.skop.fiji;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A collection of ops other than scikit-ops' own: a package of ops, and the
 * environments it brings.
 * <p>
 * scikit-ops' design 0022 lets any package be a collection -- tnia-ops,
 * biapy-ops -- and its Python runner finds a collection's environments beside
 * the op's files. Fiji's host is Java, so it cannot read the entry points that
 * register a collection in Python; it is told instead, with
 * {@code -Dskop.collections} or {@code SKOP_COLLECTIONS}: checkout folders,
 * separated by the platform's path separator ({@code ;} on Windows). Each
 * holds a {@code pyproject.toml} naming the collection, its package under
 * {@code src/}, and {@code envs/<id>/pixi.toml}.
 * <p>
 * An environment is built as {@code <name>--<id>}, which is what the Python
 * runner calls it too, so Fiji, napari and a notebook share one build.
 */
public final class OpCollection {

	private static List<OpCollection> configured;

	private final String name;
	private final String pkg;
	private final File srcRoot;
	private final File envsDir;

	/**
	 * @param name the collection's name, as its pyproject.toml gives it
	 *          ({@code biapy-ops}); names its built environments.
	 * @param pkg the top-level Python package of its ops ({@code biapy_ops}).
	 * @param srcRoot the folder holding that package, put on a worker's path.
	 * @param envsDir the folder holding {@code <env-id>/pixi.toml}.
	 */
	public OpCollection(String name, String pkg, File srcRoot, File envsDir) {
		this.name = name;
		this.pkg = pkg;
		this.srcRoot = srcRoot;
		this.envsDir = envsDir;
	}

	public String name() {
		return name;
	}

	/** {@code biapy-ops'}, {@code my-toolkit's}: for crediting it in messages. */
	public String possessive() {
		return name + (name.endsWith("s") ? "'" : "'s");
	}

	/** The top-level package of the ops: what {@code describe} is asked for. */
	public String pkg() {
		return pkg;
	}

	public File srcRoot() {
		return srcRoot;
	}

	public File envsDir() {
		return envsDir;
	}

	/** Whether an op in this Python module belongs to this collection. */
	public boolean owns(String module) {
		return module.equals(pkg) || module.startsWith(pkg + ".");
	}

	/** What an environment of this collection is built as. */
	public String builtName(String envId) {
		return name + "--" + envId;
	}

	@Override
	public String toString() {
		return name + " (" + pkg + " in " + srcRoot + ")";
	}

	/**
	 * The collections named by {@code -Dskop.collections} or
	 * {@code SKOP_COLLECTIONS}, read once. Folders that are not collection
	 * checkouts are skipped, with a message on stderr saying why.
	 */
	public static synchronized List<OpCollection> configured() {
		if (configured == null) {
			String value = System.getProperty("skop.collections");
			if (value == null || value.isEmpty()) value = System.getenv("SKOP_COLLECTIONS");
			configured = Collections.unmodifiableList(parse(value));
		}
		return configured;
	}

	/** Sets the collections, as tests do; null reads the configuration again. */
	static synchronized void configure(List<OpCollection> collections) {
		configured = collections == null ? null :
			Collections.unmodifiableList(new ArrayList<>(collections));
	}

	/** The configured collection an op's module belongs to, or null for skop's. */
	public static OpCollection of(String module) {
		for (OpCollection collection : configured()) {
			if (collection.owns(module)) return collection;
		}
		return null;
	}

	static List<OpCollection> parse(String value) {
		List<OpCollection> found = new ArrayList<>();
		if (value == null || value.trim().isEmpty()) return found;
		for (String part : value.split(Pattern.quote(File.pathSeparator))) {
			if (part.trim().isEmpty()) continue;
			try {
				found.add(fromCheckout(new File(part.trim()).getAbsoluteFile()));
			}
			catch (IOException | IllegalArgumentException exc) {
				System.err.println("[fiji-skopkit] Not an op collection, skipped: " +
					part + ": " + exc.getMessage());
			}
		}
		return found;
	}

	/**
	 * A collection from its checkout: {@code pyproject.toml}, {@code src/<pkg>}
	 * and {@code envs/}.
	 */
	public static OpCollection fromCheckout(File dir) throws IOException {
		File project = new File(dir, "pyproject.toml");
		if (!project.isFile()) throw new IllegalArgumentException("no " + project);
		String name = projectName(new String(Files.readAllBytes(project.toPath()),
			StandardCharsets.UTF_8));
		if (name == null) throw new IllegalArgumentException("no [project] name in " + project);
		File envs = new File(dir, "envs");
		if (!envs.isDirectory()) throw new IllegalArgumentException("no " + envs);
		File src = new File(dir, "src");
		String pkg = null;
		File[] children = src.listFiles();
		if (children != null) {
			java.util.Arrays.sort(children);
			for (File child : children) {
				if (new File(child, "__init__.py").isFile()) {
					pkg = child.getName();
					break;
				}
			}
		}
		if (pkg == null) throw new IllegalArgumentException("no package under " + src);
		return new OpCollection(normalized(name), pkg, src, envs);
	}

	private static String projectName(String toml) {
		int start = toml.indexOf("[project]");
		if (start < 0) return null;
		String section = toml.substring(start + "[project]".length());
		int next = section.indexOf("\n[");
		if (next >= 0) section = section.substring(0, next);
		Matcher m = Pattern.compile("(?m)^name\\s*=\\s*\"([^\"]+)\"").matcher(section);
		return m.find() ? m.group(1) : null;
	}

	/**
	 * An op module's namespace within its collection: {@code
	 * skop.ops.segment.cellpose4} is {@code [segment, cellpose4]}, and so is
	 * {@code biapy_ops.segment.watershed_by_channels}'s {@code [segment,
	 * watershed_by_channels]}. Empty for a module in no known collection.
	 */
	public static String[] namespace(String module) {
		String prefix = SkopRunner.OPS_PACKAGE + ".";
		OpCollection collection = of(module);
		if (collection != null) prefix = collection.pkg() + ".";
		if (!module.startsWith(prefix)) return new String[0];
		return module.substring(prefix.length()).split("\\.");
	}

	/**
	 * An op's name in scripts, which carries who made it. scikit-ops' own ops
	 * are {@code segment.cellpose4}, since {@code skop} already says whose they
	 * are; a collection's carry its package first, {@code
	 * biapy_ops.predict.bmz}, so a script says where an op came from -- and
	 * two collections can never share a name. Null for a module in no known
	 * collection.
	 */
	public static String scriptName(String module, String function) {
		String[] namespace = namespace(module);
		if (namespace.length == 0) return null;
		String name = namespace[0] + "." + function;
		OpCollection collection = of(module);
		return collection == null ? name : collection.pkg() + "." + name;
	}

	/**
	 * An environment as the Log names it: {@code the cellpose3 environment},
	 * or {@code biapy-ops' biapy environment}, crediting the collection.
	 */
	public static String envLabel(OpCollection collection, String envId) {
		if (collection == null) return "the " + envId + " environment";
		return collection.possessive() + " " + envId + " environment";
	}

	/** The menu an op module's commands go under: its collection's name. */
	public static String menuGroup(String module) {
		OpCollection collection = of(module);
		return collection == null ? OpModuleInfo.MENU_GROUP : collection.name();
	}

	/** As PyPI compares names, and as the Python runner names environments. */
	static String normalized(String name) {
		return name.replaceAll("[-_.]+", "-").toLowerCase();
	}
}
