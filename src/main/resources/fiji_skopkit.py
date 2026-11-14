"""scikit-ops' ops as Python functions, for Fiji's Jython scripts.

    from fiji_skopkit import skop

    labels = skop.segment.cellpose4(imp)
    labels = skop.segment.cellpose4(imp, diameter=30, model="cpsam")

    result = skop.segment.instanseg(imp, target="nuclei")
    result.nuclei
    nuclei, cells = skop.segment.instanseg(imp)

``skop.segment.cellpose4(imp, diameter=30)`` is
``Skop.run("segment.cellpose4", imp, {"diameter": 30})`` and nothing more:
the first argument is the op's image, everything else goes by name, and the
op runs exactly as it does from its menu entry. An op with several outputs
returns them by name, and they unpack in order, as a Python op's NamedTuple
does.

Another collection's ops carry its package, so a script says whose they are:
``skop.biapy_ops.predict.bmz(imp)``, never ``skop.predict.bmz(imp)``.

``dir(skop)`` lists the namespaces and ``dir(skop.segment)`` the ops in one. A
misspelt name or parameter raises ValueError, saying what was probably
meant; an op that fails raises RuntimeError with the worker's message.
"""

from java.lang import IllegalArgumentException, IllegalStateException
from java.util import HashMap, Map
from org.apposed.skop.fiji import Skop

__all__ = ["skop"]


def _skop():
    return Skop.current()


def _call(run, *args):
    """Run a Skop call, with its Java errors as Python ones."""
    try:
        return run(*args)
    except IllegalArgumentException, error:
        raise ValueError(error.getMessage())
    except IllegalStateException, error:
        cause = error.getCause()
        detail = cause.getMessage() if cause is not None else ""
        raise RuntimeError("%s: %s" % (error.getMessage(), detail))


class Outputs(object):
    """Several outputs, by name (``result.nuclei``) and in order."""

    def __init__(self, outputs):
        self._outputs = outputs

    def __getattr__(self, name):
        if name.startswith("_") or not self._outputs.containsKey(name):
            raise AttributeError("no output %r; there are: %s"
                                 % (name, ", ".join(self._outputs.keySet())))
        return self._outputs.get(name)

    def __getitem__(self, name):
        return self.__getattr__(name)

    def __iter__(self):
        return iter(list(self._outputs.values()))

    def __len__(self):
        return self._outputs.size()

    def __dir__(self):
        return list(self._outputs.keySet())

    def __repr__(self):
        return "Outputs(%s)" % ", ".join(self._outputs.keySet())


class Op(object):
    """One op, callable: ``op(image, **parameters)``."""

    def __init__(self, name):
        self.__name__ = name

    def __call__(self, *args, **kwargs):
        if len(args) > 1:
            raise TypeError("%s takes the image, then everything else by name"
                            % self.__name__)
        named = HashMap()
        for key, value in kwargs.items():
            named.put(key, value)
        current = _skop()
        if args:
            result = _call(current.run, self.__name__, args[0], named)
        else:
            result = _call(current.run, self.__name__, named)
        return Outputs(result) if isinstance(result, Map) else result

    def __repr__(self):
        return "<scikit-ops op %s>" % self.__name__


class Namespace(object):
    """The ops under one name: ``skop.segment``, ``skop.biapy_ops``,
    ``skop.biapy_ops.predict``."""

    def __init__(self, name):
        self._name = name

    def _prefix(self):
        return self._name + "." if self._name else ""

    def _names(self):
        prefix = self._prefix()
        return [name[len(prefix):] for name in _skop().names()
                if name.startswith(prefix)]

    def __getattr__(self, name):
        if name.startswith("_"):
            raise AttributeError(name)
        full = self._prefix() + name
        names = self._names()
        if name in names:
            return Op(full)
        if any(rest.startswith(name + ".") for rest in names):
            return Namespace(full)
        # Not here: say what was meant -- a collection's op is called with
        # its collection's name, skop.biapy_ops.predict.bmz.
        if not self._name:
            owners = sorted(set(
                other.split(".")[0] for other in _skop().names()
                if other.count(".") >= 2
                and other.split(".", 1)[1].startswith(name + ".")))
            if owners:
                raise AttributeError(
                    "%r is a namespace of %s; call its ops with their "
                    "collection's name: %s" % (name, " and ".join(owners),
                    ", ".join("skop.%s.%s" % (owner, name) for owner in owners)))
        _call(_skop().op, full)
        return Op(full)

    def __dir__(self):
        return sorted(set(rest.split(".")[0] for rest in self._names()))

    def __repr__(self):
        return "<scikit-ops %s: %s>" % (self._name or "ops",
                                        ", ".join(self.__dir__()))


class Ops(Namespace):
    """Every op: scikit-ops' own by namespace, ``skop.segment.cellpose4``;
    another collection's after its package, ``skop.biapy_ops.predict.bmz``."""

    def __init__(self):
        Namespace.__init__(self, "")


skop = Ops()
