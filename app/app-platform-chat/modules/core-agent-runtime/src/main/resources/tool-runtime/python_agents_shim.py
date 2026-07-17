"""Small execution shim for the OpenAI Agents SDK function_tool authoring contract."""

import inspect
import types
import typing


def _schema(annotation):
    if annotation in (inspect.Signature.empty, typing.Any):
        return {}
    if annotation is str:
        return {"type": "string"}
    if annotation is int:
        return {"type": "integer"}
    if annotation is float:
        return {"type": "number"}
    if annotation is bool:
        return {"type": "boolean"}
    if annotation in (dict,):
        return {"type": "object"}
    if annotation in (list, tuple, set):
        return {"type": "array"}
    origin = typing.get_origin(annotation)
    arguments = typing.get_args(annotation)
    if origin in (list, tuple, set):
        return {"type": "array", "items": _schema(arguments[0]) if arguments else {}}
    if origin is dict:
        return {"type": "object"}
    if origin in (typing.Union, types.UnionType):
        variants = [_schema(value) if value is not type(None) else {"type": "null"} for value in arguments]
        return {"anyOf": variants}
    return {}


class FunctionTool:
    _managed_function_tool = True

    def __init__(self, function, name=None, description=None):
        self._function = function
        self.name = name or function.__name__
        self.description = description or inspect.getdoc(function) or ""
        signature = inspect.signature(function)
        try:
            type_hints = typing.get_type_hints(function)
        except Exception:
            type_hints = {}
        properties = {}
        required = []
        self._takes_context = False
        for parameter in signature.parameters.values():
            annotation = type_hints.get(parameter.name, parameter.annotation)
            annotation_name = getattr(typing.get_origin(annotation) or annotation, "__name__", str(annotation))
            if not properties and "RunContextWrapper" in annotation_name:
                self._takes_context = True
                continue
            properties[parameter.name] = _schema(annotation)
            if parameter.default is inspect.Signature.empty:
                required.append(parameter.name)
        self.params_json_schema = {
            "type": "object",
            "properties": properties,
            "required": required,
            "additionalProperties": False,
        }
        self.output_json_schema = _schema(type_hints.get("return", signature.return_annotation))

    def _managed_invoke(self, arguments, context=None):
        if self._takes_context:
            return self._function(RunContextWrapper(context or {}), **arguments)
        return self._function(**arguments)

    def _managed_describe(self):
        return {
            "name": self.name,
            "description": self.description,
            "inputSchema": self.params_json_schema,
            "outputSchema": self.output_json_schema,
        }


def function_tool(function=None, *, name_override=None, description_override=None, **_kwargs):
    def decorate(target):
        return FunctionTool(target, name_override, description_override)

    return decorate(function) if function is not None else decorate


class RunContextWrapper:
    def __init__(self, context):
        self.context = context

    @classmethod
    def __class_getitem__(cls, _item):
        return cls
