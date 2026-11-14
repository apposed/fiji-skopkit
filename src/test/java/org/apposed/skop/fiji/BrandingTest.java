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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.apposed.skop.fiji.wire.OpSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that a collection's ops carry its name in scripts: two collections
 * with the same op never collide, and a script always says whose op it runs.
 * No Python needed.
 */
public class BrandingTest {

	private Map<String, OpSpec> ops;

	@BeforeEach
	public void setUp() {
		OpCollection.configure(Arrays.asList(
			new OpCollection("biapy-ops", "biapy_ops", new File("biapy-ops/src"),
				new File("biapy-ops/envs")),
			new OpCollection("tnia-ops", "tnia_ops", new File("tnia-ops/src"),
				new File("tnia-ops/envs"))));
		ops = new TreeMap<>();
		for (OpSpec op : Arrays.asList(
			op("skop.ops.threshold.otsu", "otsu"),
			op("biapy_ops.predict", "bmz"),
			op("tnia_ops.predict", "bmz"),
			op("biapy_ops.segment.watershed_by_channels", "watershed_by_channels")))
		{
			ops.put(Skop.shortName(op), op);
		}
	}

	@AfterEach
	public void tearDown() {
		OpCollection.configure(null);
	}

	private static OpSpec op(String module, String function) {
		return new OpSpec(module + ":" + function, module, function, "env", false,
			false, null, "", Collections.emptyList(), null, null,
			Collections.singletonList("result"), Collections.emptyList());
	}

	@Test
	public void testACollectionsOpsCarryItsName() {
		assertEquals(Arrays.asList("biapy_ops.predict.bmz",
			"biapy_ops.segment.watershed_by_channels", "threshold.otsu",
			"tnia_ops.predict.bmz"), new java.util.ArrayList<>(ops.keySet()));
	}

	@Test
	public void testTheSameOpInTwoCollectionsIsTwoNames() {
		assertEquals("biapy_ops.predict.bmz", Skop.find("biapy_ops.predict.bmz", ops));
		assertEquals("tnia_ops.predict.bmz", Skop.find("tnia_ops.predict.bmz", ops));
		assertEquals("tnia_ops.predict.bmz", Skop.find("tnia_ops.predict:bmz", ops));
	}

	@Test
	public void testAnOpSharedByCollectionsAsksWhich() {
		for (String name : Arrays.asList("predict.bmz", "bmz")) {
			IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class, () -> Skop.find(name, ops));
			assertTrue(error.getMessage().contains("biapy_ops.predict.bmz") &&
				error.getMessage().contains("tnia_ops.predict.bmz"), error.getMessage());
		}
	}

	@Test
	public void testACollectionsOpWithoutItsNameSaysWhose() {
		for (String name : Arrays.asList("segment.watershed_by_channels",
			"watershed_by_channels"))
		{
			IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class, () -> Skop.find(name, ops));
			assertTrue(error.getMessage().contains("biapy-ops' op") &&
				error.getMessage().contains("biapy_ops.segment.watershed_by_channels"),
				error.getMessage());
		}
	}

	@Test
	public void testScikitOpsOwnOpsKeepTheirShortNames() {
		assertEquals("threshold.otsu", Skop.find("threshold.otsu", ops));
		assertEquals("threshold.otsu", Skop.find("otsu", ops));
		assertEquals("threshold.otsu", Skop.find("skop.ops.threshold.otsu:otsu", ops));
	}

	@Test
	public void testTheLogCreditsACollectionsEnvironment() {
		OpCollection biapy = OpCollection.configured().get(0);
		assertEquals("biapy-ops' biapy environment", OpCollection.envLabel(biapy, "biapy"));
		assertEquals("the cellpose3 environment", OpCollection.envLabel(null, "cellpose3"));
	}
}
