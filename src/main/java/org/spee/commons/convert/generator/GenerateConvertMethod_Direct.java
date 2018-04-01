package org.spee.commons.convert.generator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.Convert;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;

class GenerateConvertMethod_Direct implements ByteCodeAppender, Implementation {
	private static final Logger LOG = LoggerFactory.getLogger(GenerateConvertMethod_Direct.class);

	private GeneratorFactory.Context<?, ?> context;
	private String methodName;
	private MappedProperties fieldMapping;


	public GenerateConvertMethod_Direct(GeneratorFactory.Context<?, ?> context, String methodName, MappedProperties mapping) {
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
	 * Write bytecode for a single property conversion. The convert call is done
	 * by InvokeDynamic. It looks somewhat like the following:
	 * 
	 * <pre>
	 * private void convertField_0(Source source, Target target){
	 *     target.setValue(convert(source.getValue()));
	 * }
	 * </pre>
	 */
	@Override
	public ByteCodeAppender.Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
		ParameterDescription source = instrumentedMethod.getParameters().get(0);
		final MethodDescription.ForLoadedMethod sourceMethodDesc = new MethodDescription.ForLoadedMethod(fieldMapping.getSourceProperty().getReadMethod());
		ParameterDescription target = instrumentedMethod.getParameters().get(1);
		final MethodDescription.ForLoadedMethod targetMethodDesc = new MethodDescription.ForLoadedMethod(fieldMapping.getTargetProperty().getWriteMethod());
		final TypeDescription targetType = targetMethodDesc.getParameters().get(0).getType().asErasure();

		LOG.debug("Mapping property {}", fieldMapping);

		StackManipulation.Size size = new StackManipulation.Compound(
										MethodVariableAccess.load(target),
										MethodVariableAccess.load(source),
										MethodInvocation.invoke(sourceMethodDesc),
										GeneratorFactory.MappingLocatorInvokeDynamic.dynamic(GeneratorFactory.CONVERT_METHODNAME, targetType, asList(sourceMethodDesc.getReturnType().asErasure()), emptyList()),
										MethodInvocation.invoke(targetMethodDesc),
										MethodReturn.VOID
							).apply(methodVisitor, implementationContext);
		return new ByteCodeAppender.Size(size.getMaximalSize(), instrumentedMethod.getStackSize());
	}


	public Builder<Convert<?, ?>> generateConvertMethod(Builder<Convert<?, ?>> builder) {
		builder = builder.defineMethod(methodName, Void.TYPE, Visibility.PRIVATE)
						.withParameter(context.sourceType)
						.withParameter(context.targetType)
						.intercept(this);
		return builder;
	}

}
