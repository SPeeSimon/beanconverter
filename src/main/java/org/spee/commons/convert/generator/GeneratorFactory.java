package org.spee.commons.convert.generator;


import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.parameterizedType;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.spee.commons.convert.generator.ClassGeneratorHelper.filterOnlyCustomConverters;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.Convert;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;
import org.spee.commons.convert.internals.MappingLocator;

import com.google.common.base.Converter;
import com.google.common.collect.Iterables;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldDescription.Latent;
import net.bytebuddy.description.field.FieldDescription.Token;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Loaded;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.TypeCasting;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation.WithImplicitInvocationTargetType;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.RandomString;

/**
 * Generating the converter. It looks like:
<code>
package generated.converters;
public class gen_Source_TypetoTargetTypeConverter extends Object {

	public ExampleConverter(){
		super();
		initCustomConverters();
	}
	
	private Converter<A,B> converter_1;

	public void initCustomConverters(){
		converter_1 = BeanCreationStrategy.newInstance(CustomConverter.class);
	}

	public void convert(Source source, Target target){
		target.setValue0( convert(source.getValue0(), TargetValue0.class) );
		target.setValue1( (TargetType)converter_1.convert(source.getValue1()) );
		// target.setValue2( convert(MapUtils.getValue(source.getValue(), convert("key"))) );
		// List t = new ArrayList();
		// target.setvalue(t);
		// CollectionUtils.transformInto(source.getList(), t, fieldTransformer());
	}
	
	public Target convert(Source source){
		if( source == null ) return null;
		Target t = BeanCreationStrategy.newInstance(Target.class);
		convert(source, t);
		return t;
	}
}
</code>
 */
public class GeneratorFactory {
	private static final String INIT_CUSTOM_CONVERTERS_METHOD = "initCustomConverters";
	private static final String CONVERTER_FIELDNAME_PREFIX = "converter_";
	private static final String CONVERT_METHODNAME = "convert";

	private static final Logger LOG = LoggerFactory.getLogger(GeneratorFactory.class);
	/**
	 * Parent class for generated converters
	 */
	private static final Class<Convert> parentClass = Convert.class;

	
	static class Context {
		String className;
		String internalClassName;
		Type sourceType;
		Type targetType;
		ClassMap classMap = new ClassMap(null, null);
		Map<Class<?>, ConverterField> customConverters = new LinkedHashMap<>();
		
		static class ConverterField {
			String fieldName;
			net.bytebuddy.description.field.FieldDescription.Token field;
			
			public ConverterField(String fieldName, Token field) {
				super();
				this.fieldName = fieldName;
				this.field = field;
			}
		}
	}
	

	public GeneratorFactory() {
		// invoke dynamic BootstrapMethod (BSM) for converters and constructors
	}

	
	public Class<?> build(ClassMap classMap){
		Context context = new Context();
		context.sourceType = (classMap.getSource().getBeanDescriptor().getBeanClass());
		context.targetType = (classMap.getTarget().getBeanDescriptor().getBeanClass());
		context.classMap = classMap;

		return build(context);
	}

	
	public Class<?> build(Class<?> sourceClass, Class<?> targetClass){
		Context context = new Context();
		context.sourceType = (sourceClass);
		context.targetType = (targetClass);
		context.classMap = ClassMapBuilder.build(sourceClass, targetClass).useDefaults().generate();
		
		return build(context);
	}
	
	private static Builder<Convert<?, ?>> generateClassDefinition(Context context){
		@SuppressWarnings("unchecked")
		Builder<Convert<?,?>> builder = (Builder<Convert<?,?>>)
				new ByteBuddy()
				.with(new NamingStrategy.AbstractBase() {
					@Override
					public String subclass(Generic superClass) {
						String src = superClass.getTypeArguments().get(0).getTypeName() + " to " + superClass.getTypeArguments().get(1).getTypeName();
						return "generated.converters.gen_" + new String(Base64.getEncoder().encode(src.getBytes())).replace("=", "");
					}
					@Override
					protected String name(TypeDescription superClass) {
						return "generated.converters_gen_Source_TypetoTargetTypeConverter" + RandomString.make(10);
					}
				})
				.subclass(parameterizedType(parentClass, context.sourceType, context.targetType).build(), ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.defineMethod("toString", String.class, Visibility.PUBLIC)
					.intercept(new Implementation.Simple(new TextConstant(String.format("converter %s to %s", context.sourceType, context.targetType)), MethodReturn.REFERENCE));
		return builder;
	}
	
	
	private static Class<Convert<?,?>> build(Context context) {
		Builder<Convert<?,?>> builder = generateClassDefinition(context);
		builder = builder.defineConstructor(Visibility.PUBLIC).intercept(SuperMethodCall.INSTANCE.andThen(new ConstructorBuilder()));
		builder = generateInitCustomConverters(context, builder);
		builder = generate2ArgsConvertMethod(context, builder);
		builder = generateConvertMethod(builder);
		
		Loaded<Convert<?,?>> loaded = builder.make().load(GeneratorFactory.class.getClassLoader());
		try {
			java.nio.file.Files.write(Paths.get("target/convertAtoB.class"), loaded.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
		Class<Convert<?,?>> dynamicType = (Class<Convert<?,?>>) loaded.getLoaded();
		return dynamicType;
	}


	/**
	 * Generate the method <code>public void convert(source, target)</code>
	 * @param context
	 * @param builder
	 * @return
	 */
	private static Builder<Convert<?, ?>> generate2ArgsConvertMethod(Context context, Builder<Convert<?, ?>> builder) {
		builder = builder.defineMethod(CONVERT_METHODNAME, Void.TYPE, Visibility.PUBLIC)
			.withParameter(context.sourceType)
			.withParameter(context.targetType)
			.intercept( new Implementation.Compound(new ClassGeneratorHelper.NullcheckAndReturnBuilder(false), new Converter2Builder(context)) );
		return builder;
	}


	/**
	 * Generate the method <code>public target convert(source)</code>
	 * @param builder
	 * @return
	 */
	private static Builder<Convert<?, ?>> generateConvertMethod(Builder<Convert<?, ?>> builder) {
		builder = builder.method( isDeclaredBy(parentClass).and(named(CONVERT_METHODNAME)) )
				.intercept(new Implementation.Compound(new ClassGeneratorHelper.NullcheckAndReturnBuilder(), new Converter1Builder()));
		return builder;
	}


	/**
	 * Create a signature for the field of a custom converter.
	 * So
	 * <pre>Converter<String,Integer> field</pre>
	 * returns
	 * <pre>Lcom/google/common/base/Converter<Ljava/lang/String;Ljava/lang/Integer;>;</pre>
	 * @param mappedProperties
	 * @return
	 */
	static class FieldInitializer implements Implementation {
		private FieldDescription.Token field;
		private Class<?> customConverter;
		private WithImplicitInvocationTargetType invokeDynamic;
		
		public FieldInitializer(FieldDescription.Token field, Class<?> customConverter) {
			super();
			this.field = field;
			this.customConverter = customConverter;
			try {
				invokeDynamic = MethodInvocation.invoke( new MethodDescription.ForLoadedMethod( BeanCreationStrategy.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class) ) );
			} catch (NoSuchMethodException | SecurityException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public InstrumentedType prepare(InstrumentedType instrumentedType) {
			return instrumentedType;
		}

		@Override
		public ByteCodeAppender appender(Target implementationTarget) {
			// converter_? = newInstance(customconverter?.class);
			return new Implementation.Simple(
					MethodVariableAccess.loadThis(),
					invokeDynamic.dynamic("newInstance", new TypeDescription.ForLoadedType(customConverter), Collections.<TypeDescription>emptyList(), Collections.emptyList()),
					TypeCasting.to(field.getType()),
					FieldAccess.forField( new Latent(implementationTarget.getInstrumentedType(), field) ).write()
			).appender(implementationTarget);
		}
		
	}
	

	/**
	 * Generate a private field per custom converter and a method to initialize them.
	 * This method is called by the constructor.
	 * The <code>newInstance</code> is a dynamic call to retrieve the custom converter. 
	 * <pre>
	 * private Converter converter_0;
	 * private Converter converter_1;
	 * 
	 * private void initCustomConverters(){
	 *     converter_0 = newInstance([customconverter.class]);
	 *     converter_1 = newInstance([anothercustomconverter.class]);
	 * }
	 * </pre>
	 * @param cw
	 * @param context
	 */
	private static <T> Builder<T> generateInitCustomConverters(GeneratorFactory.Context context, Builder<T> builder) {
		List<Implementation> initConverters = new ArrayList<>();
		int index = 0;

		for (MappedProperties mappedProperties : Iterables.filter(context.classMap.getMappedProperties(), filterOnlyCustomConverters())) {
			Generic build = parameterizedType(Converter.class, mappedProperties.getSourceProperty().getReadMethod().getGenericReturnType(), mappedProperties.getTargetProperty().getWriteMethod().getParameterTypes()[0]).build();
			String fieldName = CONVERTER_FIELDNAME_PREFIX + (index++);
			FieldDescription.Token token = new FieldDescription.Token(fieldName, Opcodes.ACC_PRIVATE, build);
			
			LOG.debug("converter field {} = {}", fieldName, build);

			builder = builder.defineField(token.getName(), token.getType(), token.getModifiers());
			initConverters.add(new FieldInitializer(token, mappedProperties.getCustomConverter()));
			context.customConverters.put(mappedProperties.getCustomConverter(), new Context.ConverterField(fieldName, token));
		}

		initConverters.add(new Implementation.Simple(MethodReturn.VOID));
		builder = builder.defineMethod(INIT_CUSTOM_CONVERTERS_METHOD, Void.TYPE, Visibility.PRIVATE).intercept(new Implementation.Compound(initConverters));
		return builder;
	}
	


	
	
	/**
	 * Create the default constructor.
	 * If there are custom converters, a call to the initialize method is also added.
	 * <code>
	 * public Classname(){
	 * 	 super();
	 *   initCustomConverters();
	 * }
	 * </code>
	 * @param cw
	 * @param parentClass
	 * @param context
	 */
	static class ConstructorBuilder implements Implementation {
		@Override
		public InstrumentedType prepare(InstrumentedType instrumentedType) {
			return instrumentedType;
		}
		
		@Override
		public ByteCodeAppender appender(Target implementationTarget) {
			return new Implementation.Simple(
					MethodVariableAccess.loadThis(),
					MethodInvocation.invoke(implementationTarget.getInstrumentedType().getDeclaredMethods().filter(ElementMatchers.named(INIT_CUSTOM_CONVERTERS_METHOD)).getOnly()),
					MethodReturn.VOID
			).appender(implementationTarget);
		}
	}



	/**
	 * Generate the method <code>public target convert(source)</code>
	 * <code>
	 * public T convert(S source){
	 * 		if( source == null ) return null;
	 * 		T target = newInstance(T.class);
	 *      this.convert(source, target);
	 *      return target;
	 * }
	 * </code>
	 */
	static class Converter1Builder implements ByteCodeAppender, Implementation {
		@Override
		public InstrumentedType prepare(InstrumentedType instrumentedType) {
			return instrumentedType;
		}
		
		@Override
		public ByteCodeAppender appender(Target implementationTarget) {
			return this;
		}
		
		@Override
		public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
/*
		nullCheckWithReturn(mv, REFINSTANCE_ARGUMENT_1);
		// create new instance
		mv.visitInvokeDynamicInsn("newInstance", getMethodDescriptor(context.targetType), newinstanceBootstrapMethod);
		mv.visitVarInsn(ASTORE, LOCAL_VAR_1);

		// this.convert(source, target)
		mv.visitVarInsn(ALOAD, REFINSTANCE_THIS);
		mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_1);
		mv.visitVarInsn(ALOAD, LOCAL_VAR_1);
		mv.visitMethodInsn(INVOKEVIRTUAL, context.internalClassName, "convert", getMethodDescriptor(VOID_TYPE, context.sourceType, context.targetType), false);
		
		// return target
		mv.visitVarInsn(ALOAD, LOCAL_VAR_1);
		mv.visitInsn(context.targetType.getOpcode(IRETURN));
	 */
            WithImplicitInvocationTargetType invokeDynamic;
			try {
				invokeDynamic = MethodInvocation.invoke( new MethodDescription.ForLoadedMethod( BeanCreationStrategy.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class) ) );
			} catch (NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			
			final ParameterDescription parameterDescription = instrumentedMethod.getParameters().get(0);			
			final int LOCAL_FIELD_OFFSET = parameterDescription.getOffset() +1;
			final TypeDescription returnType = 
					new TypeDescription.Latent(instrumentedMethod.getReturnType().getTypeName(), instrumentedMethod.getReturnType().getModifiers(), instrumentedMethod.getReturnType(), instrumentedMethod.getReturnType().getInterfaces());
			MethodVariableAccess returnVar = MethodVariableAccess.of(returnType);

			
			List<StackManipulation> insr = new ArrayList<>();
			insr.add( invokeDynamic.dynamic("", returnType, Collections.<TypeDescription>emptyList(), Collections.emptyList()) );
			insr.add(returnVar.storeAt(LOCAL_FIELD_OFFSET));
			
			insr.add(MethodVariableAccess.loadThis());
			insr.add(MethodVariableAccess.load(parameterDescription));
			insr.add(returnVar.loadFrom(LOCAL_FIELD_OFFSET));
			insr.add(MethodInvocation.invoke(implementationContext.getInstrumentedType().getDeclaredMethods().filter(named(CONVERT_METHODNAME).and(takesArguments(2))).getOnly()));
			
			insr.add(returnVar.loadFrom(LOCAL_FIELD_OFFSET));
			insr.add(MethodReturn.of(returnType));
			
			net.bytebuddy.implementation.bytecode.StackManipulation.Size size = new StackManipulation.Compound(insr).apply(methodVisitor, implementationContext);
			return new Size(size.getMaximalSize()+1, instrumentedMethod.getStackSize()+1);
		}
	}



	static class Converter2Builder implements ByteCodeAppender, Implementation {
		private GeneratorFactory.Context context;
		private static WithImplicitInvocationTargetType invokeConvert;

		public Converter2Builder(GeneratorFactory.Context context) {
			this.context = context;
			try {
				invokeConvert = MethodInvocation.invoke( new MethodDescription.ForLoadedMethod( MappingLocator.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class) ) );
			} catch (NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}

		@Override
		public InstrumentedType prepare(InstrumentedType instrumentedType) {
			return instrumentedType;
		}
		
		@Override
		public ByteCodeAppender appender(Target implementationTarget) {
			return this;
		}
		
		
		enum ConverterStackManipulators {
			DIRECT{
				/**
				 * Write bytecode for a single property conversion.
				 * The convert call is done by InvokeDynamic. It looks somewhat like the following:
				 * <pre>
				 * target.setValue( convert(source.getValue()) );
				 * </pre>
				 */
				StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target, GeneratorFactory.Context context, Implementation.Context implementationContext){
					final MethodDescription.ForLoadedMethod sourceMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getSourceProperty().getReadMethod());
					final MethodDescription.ForLoadedMethod targetMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getTargetProperty().getWriteMethod());
					final TypeDescription targetType = targetMethodDesc.getParameters().get(0).getType().asErasure();
					
					LOG.debug("convert: read={} ,write={}", fieldMap.getSourceProperty().getReadMethod(), fieldMap.getTargetProperty().getWriteMethod());

					return new StackManipulation.Compound(
							MethodVariableAccess.load(target),
							MethodVariableAccess.load(source),
							MethodInvocation.invoke( sourceMethodDesc ),
							invokeConvert.dynamic(CONVERT_METHODNAME, targetType, Arrays.asList(sourceMethodDesc.getReturnType().asErasure()), Collections.emptyList()),
							MethodInvocation.invoke( targetMethodDesc )
					);
				}				
			},
			USING_FIELD{
				/**
				 * Write bytecode for a single property conversion, where there should be used a specific converter.
				 * This converter is gotten from a local field.
				 * <pre>
				 * target.setValue( (TargetType)converter_0.convert(source.getValue()) );
				 * </pre>
				 */
				StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target, GeneratorFactory.Context context, Implementation.Context implementationContext) {
					final MethodDescription.ForLoadedMethod sourceMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getSourceProperty().getReadMethod());
					final MethodDescription.ForLoadedMethod targetMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getTargetProperty().getWriteMethod());
					
					LOG.debug("convert: read={} ,write={}, converter={}", fieldMap.getSourceProperty().getReadMethod(), fieldMap.getTargetProperty().getWriteMethod(), fieldMap.getCustomConverter());
					
					/*
		mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_2);
		mv.visitVarInsn(ALOAD, REFINSTANCE_THIS);
		mv.visitFieldInsn(GETFIELD, context.internalClassName, field.fieldName, converterType.getDescriptor());
		mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_1);
		mv.visitMethodInsn(INVOKEVIRTUAL, context.sourceType.getDescriptor(), sourceGetter.getName(), getMethodDescriptor(sourceGetter), false);
		mv.visitMethodInsn(INVOKEVIRTUAL, converterType.getInternalName(), "convert", getMethodDescriptor(objectType, objectType), false);

		if( setterArgumentType.isPrimitive() ){
			// Additional conversion needed for wrapper
			final Type wrapperType = getType(Primitives.wrap(setterArgumentType));
			mv.visitTypeInsn(CHECKCAST, wrapperType.getInternalName());
			mv.visitInvokeDynamicInsn("convert", getMethodDescriptor(getType(setterArgumentType), wrapperType), converterBootstrapMethod, "");
		}else{
			mv.visitTypeInsn(CHECKCAST, setterArgumentType.getTypeName());						
		}
		mv.visitMethodInsn(INVOKEVIRTUAL, context.targetType.getDescriptor(), targetSetter.getName(), getMethodDescriptor(targetSetter), false);
					 */
					
					FieldDescription.Token field = context.customConverters.get(fieldMap.getCustomConverter()).field;
					try {
						return new StackManipulation.Compound(
							MethodVariableAccess.load(target),
							MethodVariableAccess.loadThis(),
							FieldAccess.forField(new FieldDescription.Latent(implementationContext.getInstrumentedType().asErasure(), field)).read(),
							MethodVariableAccess.load(source),
							MethodInvocation.invoke(sourceMethodDesc),
							MethodInvocation.invoke(new MethodDescription.ForLoadedMethod(Converter.class.getMethod(CONVERT_METHODNAME, Object.class))),
							TypeCasting.to(targetMethodDesc.getParameters().get(0).getType()),
							MethodInvocation.invoke(targetMethodDesc)
						);
					} catch (NoSuchMethodException | SecurityException e) {
					}
					
					return null;
				}
			};
			
			
			abstract StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target, GeneratorFactory.Context context, Implementation.Context implementationContext);
		}
		
		
		@Override
		public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
			ParameterDescription source = instrumentedMethod.getParameters().get(0);
			ParameterDescription target = instrumentedMethod.getParameters().get(1);
			List<StackManipulation> insr = new ArrayList<>();
			
			LOG.debug("Mapping {} properties", this.context.classMap.getMappedProperties().size());
			int incr = 0;
			for(MappedProperties fieldMap : this.context.classMap.getMappedProperties()){
				if( !fieldMap.hasCustomConverter() ) {
					insr.add( ConverterStackManipulators.DIRECT.getCode(fieldMap, source, target, this.context, implementationContext) );
				}else{
					insr.add( ConverterStackManipulators.USING_FIELD.getCode(fieldMap, source, target, this.context, implementationContext) );
					incr++;
				}
			}
			
			insr.add(MethodReturn.VOID);
			net.bytebuddy.implementation.bytecode.StackManipulation.Size size = new StackManipulation.Compound(insr).apply(methodVisitor, implementationContext);
			return new Size(size.getMaximalSize()+incr, instrumentedMethod.getStackSize()+incr);
		}

	}

}
