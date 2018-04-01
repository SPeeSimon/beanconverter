package org.spee.commons.convert.generator;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.Convert;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.TypeCasting;
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveUnboxingDelegate;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;

class GenerateConvertMethod_WithFieldUsage implements ByteCodeAppender, Implementation {
	private static final Logger LOG = LoggerFactory.getLogger(GenerateConvertMethod_WithFieldUsage.class);

	private GeneratorFactory.Context<?, ?> context;
	private String methodName;
	private MappedProperties fieldMapping;


	public GenerateConvertMethod_WithFieldUsage(GeneratorFactory.Context<?, ?> context, String methodName, MappedProperties mapping) {
		this.context = context;
		this.methodName = methodName;
		this.fieldMapping = mapping;
	}


	@Override
	public InstrumentedType prepare(InstrumentedType instrumentedType) {
		return instrumentedType;
	}


	@Override
	public ByteCodeAppender appender(Target implementationTarget) {
		return this;
	}


	/**
	 * Write bytecode for a single property conversion, where there should be
	 * used a specific converter. This converter is gotten from a local field.
	 * 
	 * <pre>
	 * private void convertField_0(Source source, Target target){
	 *     target.setValue((TargetType) converter_0.convert(source.getValue()));
	 * }
	 * </pre>
	 */
	public StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target,
			Implementation.Context implementationContext) {
		final MethodDescription.ForLoadedMethod sourceMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getSourceProperty().getReadMethod());
		final MethodDescription.ForLoadedMethod targetMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getTargetProperty().getWriteMethod());
		final FieldDescription.Token field = context.customConverters.get(fieldMap.getCustomConverter()).field;
		final Generic targetType = targetMethodDesc.getParameters().get(0).getType();
		final TypeDescription wrapperType = ClassGeneratorHelper.getPrimitiveWrapperType(targetType); // if target is primitive, use wrapper
		final List<StackManipulation> insr = new ArrayList<>(9);

		LOG.debug("convert: read={} ,write={}, converter={} (primitive:{})", fieldMap.getSourceProperty().getReadMethod(),
				fieldMap.getTargetProperty().getWriteMethod(), fieldMap.getCustomConverter(), wrapperType != null);

		insr.add(MethodVariableAccess.load(target));
		insr.add(MethodVariableAccess.loadThis());
		insr.add(FieldAccess.forField(new FieldDescription.Latent(implementationContext.getInstrumentedType().asErasure(), field)).read());
		insr.add(MethodVariableAccess.load(source));
		insr.add(MethodInvocation.invoke(sourceMethodDesc));
		insr.add(GeneratorFactory.ConverterMethodInvoke);
		if (wrapperType != null) {
			insr.add(TypeCasting.to(wrapperType));
			insr.add(PrimitiveUnboxingDelegate.forReferenceType(wrapperType).assignUnboxedTo(targetType, null, null));
		} else {
			insr.add(TypeCasting.to(targetType.asErasure()));
		}
		insr.add(MethodInvocation.invoke(targetMethodDesc));

		return new StackManipulation.Compound(insr);
	}


	@Override
	public ByteCodeAppender.Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
		ParameterDescription source = instrumentedMethod.getParameters().get(0);
		ParameterDescription target = instrumentedMethod.getParameters().get(1);

		LOG.debug("Mapping property {}", fieldMapping);
		int incr_stack = 1;

		StackManipulation.Size size = new StackManipulation.Compound(
									getCode(fieldMapping, source, target, implementationContext),
									MethodReturn.VOID
							).apply(methodVisitor, implementationContext);
		return new ByteCodeAppender.Size(size.getMaximalSize() + incr_stack, instrumentedMethod.getStackSize() + incr_stack);
	}


	public Builder<Convert<?, ?>> generateConvertMethod(Builder<Convert<?, ?>> builder) {
		builder = builder.defineMethod(methodName, Void.TYPE, Visibility.PRIVATE)
						.withParameter(context.sourceType)
						.withParameter(context.targetType)
						.intercept(this);
		return builder;
	}

}
