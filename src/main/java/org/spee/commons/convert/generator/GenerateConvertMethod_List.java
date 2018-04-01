package org.spee.commons.convert.generator;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.Convert;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;
import org.spee.commons.utils.IterableUtils;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.Implementation.Target;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.TypeCasting;
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveUnboxingDelegate;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;

public class GenerateConvertMethod_List implements ByteCodeAppender, Implementation {
	private static final Logger LOG = LoggerFactory.getLogger(GenerateConvertMethod_WithFieldUsage.class);

	private GeneratorFactory.Context<?, ?> context;
	private String methodName;
	private MappedProperties fieldMapping;


	public GenerateConvertMethod_List(GeneratorFactory.Context<?, ?> context, String methodName, MappedProperties mapping) {
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
	 * 
	 * <pre>
	 * private void convertField_0(Source source, Target target){
	 *     target.setValue((TargetType) converter_0.convert(source.getValue()));
	 * }
	 * </pre>
	 */
	public StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target, Implementation.Context implementationContext) {
		final InDefinedShape sourceMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getSourceProperty().getReadMethod());
		final InDefinedShape targetMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getTargetProperty().getWriteMethod());
/*
 5  aload_1
 6  invokemethod source.getValue() : java.lang.Object[]
 7  invokestatic org.spee.commons.utils.IterableUtils.of(java.lang.Object[]) : java.lang.Iterable
 8  invokeinterface java.lang.Iterable.iterator() : java.util.Iterator
14  astore_3
15  goto 35
18  aload_3
19  invokeinterface java.util.Iterator.next() : java.lang.Object
// convert
35  aload_3
36  invokeinterface java.util.Iterator.hasNext() : boolean
41  ifne 18
44  return
 */
		InDefinedShape toIterableMethodDesc = new MethodDescription.Latent(new TypeDescription.ForLoadedType(IterableUtils.class), new MethodDescription.Token("of", Modifier.PUBLIC & Modifier.STATIC, null));
		final Generic targetType = targetMethodDesc.getParameters().get(0).getType();
		final TypeDescription wrapperType = ClassGeneratorHelper.getPrimitiveWrapperType(targetType); // if target is primitive, use wrapper

		LOG.debug("convert: read={} ,write={}, converter={} (primitive:{})", fieldMap.getSourceProperty().getReadMethod(),
				fieldMap.getTargetProperty().getWriteMethod(), fieldMap.getCustomConverter(), wrapperType != null);

		return null;
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

