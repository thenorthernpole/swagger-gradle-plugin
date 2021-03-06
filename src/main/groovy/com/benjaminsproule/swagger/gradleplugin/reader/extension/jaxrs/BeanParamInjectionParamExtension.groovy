package com.benjaminsproule.swagger.gradleplugin.reader.extension.jaxrs

import com.sun.jersey.api.core.InjectParam
import com.sun.jersey.core.header.FormDataContentDisposition
import io.swagger.annotations.ApiParam
import io.swagger.jaxrs.ext.AbstractSwaggerExtension
import io.swagger.jaxrs.ext.SwaggerExtension
import io.swagger.models.parameters.AbstractSerializableParameter
import io.swagger.models.parameters.Parameter
import io.swagger.models.parameters.SerializableParameter
import io.swagger.models.properties.PropertyBuilder
import io.swagger.util.AllowableValues
import io.swagger.util.AllowableValuesUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.reflect.TypeUtils

import javax.ws.rs.BeanParam
import java.lang.annotation.Annotation
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type

class BeanParamInjectionParamExtension extends AbstractSwaggerExtension {
    @Override
    List<Parameter> extractParameters(List<Annotation> annotations, Type type, Set<Type> typesToSkip, Iterator<SwaggerExtension> chain) {
        def cls = TypeUtils.getRawType(type, type)

        List<Parameter> output = []
        if (shouldIgnoreClass(cls) || typesToSkip.contains(type)) {
            // stop the processing chain
            typesToSkip += type
            return output
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof BeanParam || annotation instanceof InjectParam) {
                return extractParametersFromAnnotation(cls)
            }
        }
        if (chain.hasNext()) {
            return chain.next().extractParameters(annotations, type, typesToSkip, chain)
        }
        return []
    }

    private List<Parameter> extractParametersFromAnnotation(Class<?> cls) {
        List<Parameter> parameters = []

        for (AccessibleObject accessibleObject : getDeclaredAndInheritedFieldsAndMethods(cls)) {
            SerializableParameter parameter = null

            int i = 0
            int apiParaIdx = -1
            boolean hidden = false

            for (Annotation annotation : accessibleObject.getAnnotations()) {
                if (annotation instanceof ApiParam) {
                    if (((ApiParam) annotation).hidden()) {
                        hidden = true
                    } else {
                        apiParaIdx = i
                    }
                }
                i++
                Type paramType = extractType(accessibleObject, cls)
                parameter = JaxrsParameterExtension.getParameter(paramType, parameter, annotation)
            }

            if (parameter != null) {
                if (apiParaIdx != -1) {
                    ApiParam param = (ApiParam) accessibleObject.getAnnotations()[apiParaIdx]
                    parameter.setDescription(param.value())
                    parameter.setRequired(param.required())
                    parameter.setAccess(param.access())

                    if (parameter instanceof AbstractSerializableParameter && StringUtils.isNotEmpty(param.defaultValue())) {
                        ((AbstractSerializableParameter) parameter).setDefaultValue(param.defaultValue())
                    }

                    AllowableValues allowableValues = AllowableValuesUtils.create(param.allowableValues())
                    if (allowableValues != null) {
                        Map<PropertyBuilder.PropertyId, Object> args = allowableValues.asPropertyArguments()
                        if (args.containsKey(PropertyBuilder.PropertyId.ENUM)) {
                            parameter.setEnum((List<String>) args.get(PropertyBuilder.PropertyId.ENUM))
                        }
                    }

                    if (!param.name().isEmpty()) {
                        parameter.setName(param.name())
                    }
                }
                if (!hidden) {
                    parameters += parameter
                }
            }
        }

        return parameters
    }

    @Override
    boolean shouldIgnoreClass(Class<?> cls) {
        return FormDataContentDisposition == cls
    }

    private List<AccessibleObject> getDeclaredAndInheritedFieldsAndMethods(Class<?> clazz) {
        List<AccessibleObject> accessibleObjects = []
        recurseGetDeclaredAndInheritedFields(clazz, accessibleObjects)
        recurseGetDeclaredAndInheritedMethods(clazz, accessibleObjects)
        return accessibleObjects
    }

    private void recurseGetDeclaredAndInheritedFields(Class<?> clazz, List<AccessibleObject> fields) {
        fields.addAll(Arrays.asList(clazz.getDeclaredFields()))
        Class<?> superClass = clazz.getSuperclass()
        if (superClass != null) {
            recurseGetDeclaredAndInheritedFields(superClass, fields)
        }
    }

    private void recurseGetDeclaredAndInheritedMethods(Class<?> clazz, List<AccessibleObject> methods) {
        methods.addAll(Arrays.asList(clazz.getDeclaredMethods()))
        Class<?> superClass = clazz.getSuperclass()
        if (superClass != null) {
            recurseGetDeclaredAndInheritedMethods(superClass, methods)
        }
    }

    private static Type extractType(AccessibleObject accessibleObject, Type defaulType) {
        if (accessibleObject instanceof Field) {
            return ((Field) accessibleObject).getGenericType()
        } else if (accessibleObject instanceof Method) {
            Method method = (Method) accessibleObject
            if (method.getParameterTypes().length == 1) {
                return method.getParameterTypes()[0]
            }
        }
        return defaulType
    }
}
