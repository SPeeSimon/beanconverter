package org.spee.commons.convert.generator;


import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.parameterizedType;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.spee.commons.convert.generator.ClassGeneratorHelper.filterOnlyCustomConverters;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveUnboxingDelegate;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation.WithImplicitInvocationTargetType;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;

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
		// TODO target.setValue2( convert(MapUtils.getValue(source.getValue(), convert("key"))) );
		// TODO List t = new ArrayList();
		// TODO target.setvalue(t);
		// TODO CollectionUtils.transformInto(source.getList(), t, fieldTransformer());
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
	static final String DEFAULT_PACKAGE = "gen.spee.commons.converter";
	private static final String INIT_CUSTOM_CONVERTERS_METHOD = "initCustomConverters";
	private static final String CONVERTER_FIELDNAME_PREFIX = "converter_";
	private static final String CONVERT_METHODNAME = "convert";
	private static final Class<Converter> INTERNAL_CONVERTER_TYPE = Converter.class;

	private static final Logger LOG = LoggerFactory.getLogger(GeneratorFactory.class);
	/**
	 * Parent class for generated converters
	 */
	@SuppressWarnings("rawtypes")
	private static final Class<Convert> parentClass = Convert.class;

	private static WithImplicitInvocationTargetType BeanCreationStrategyinvokeDynamic;
	private static WithImplicitInvocationTargetType MappingLocatorInvokeDynamic;
	private static WithImplicitInvocationTargetType ConverterMethodInvoke;

	static {
		try {
			// invoke dynamic BootstrapMethod (BSM) for converters and constructors
			BeanCreationStrategyinvokeDynamic = MethodInvocation.invoke( new MethodDescription.ForLoadedMethod( BeanCreationStrategy.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class) ) );
			MappingLocatorInvokeDynamic = MethodInvocation.invoke( new MethodDescription.ForLoadedMethod( MappingLocator.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class) ) );
			// Direct method invoker on converter
			ConverterMethodInvoke = MethodInvocation.invoke( new MethodDescription.ForLoadedMethod( INTERNAL_CONVERTER_TYPE.getMethod(CONVERT_METHODNAME, Object.class) ) );
		} catch (NoSuchMethodException | SecurityException e) {
			// we are sure of their existence, since they are bundled
		}
	}
	
	
	static class Context<S,T> {
		String className;
		String internalClassName;
		Type sourceType;
		Type targetType;
		ClassMap classMap = new ClassMap(null, null);
		Map<Class<?>, ConverterField> customConverters = new LinkedHashMap<>();
		
		public Context(Class<S> source, Class<T> target) {
			this.sourceType = source;
			this.targetType = target;
		}
		
		static class ConverterField {
			String fieldName;
			FieldDescription.Token field;
			
			public ConverterField(String fieldName, Token field) {
				super();
				this.fieldName = fieldName;
				this.field = field;
			}
		}
	}


	@SuppressWarnings("rawtypes")
	public Class<Convert<?,?>> build(ClassMap classMap){
		Context context = new Context<>(classMap.getSource().getBeanDescriptor().getBeanClass(),
								classMap.getTarget().getBeanDescriptor().getBeanClass());
		context.classMap = classMap;
		return build(context);
	}

	
	public <S,T> Class<Convert<S,T>> build(Class<S> sourceClass, Class<T> targetClass){
		Context<S,T> context = new Context<>(sourceClass, targetClass);
		context.classMap = ClassMapBuilder.build(sourceClass, targetClass).useDefaults(true).generate();
		return build(context);
	}
	
	
	@SuppressWarnings("unchecked")
	private static <S,T> Class<Convert<S,T>> build(Context<S,T> context) {
		Builder<Convert<?,?>> builder = generateClassDefinition(context)
					.defineConstructor( Visibility.PUBLIC ).intercept( SuperMethodCall.INSTANCE.andThen(ConstructorBuilder.INSTANCE) );
		builder = generateInitCustomConverters(context, builder);
		builder = generate2ArgsConvertMethod(context, builder);
		builder = generateConvertMethod(builder);
		
		Loaded<Convert<?,?>> loaded = builder.make().load(GeneratorFactory.class.getClassLoader());
		Path saveTo = Paths.get("target/", loaded.getTypeDescription().getName() + ".class");
		try {
			LOG.debug("Writing class bytes to file: {}", saveTo);
			java.nio.file.Files.write(saveTo, loaded.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			LOG.warn("Could not write class bytes to file {}: {}", saveTo, e.getMessage(), e);
		}

		return (Class<Convert<S,T>>) loaded.getLoaded();
	}	
	
	
	private static Builder<Convert<?, ?>> generateClassDefinition(Context<?,?> context){
		@SuppressWarnings("unchecked")
		Builder<Convert<?,?>> builder = (Builder<Convert<?,?>>)
				new ByteBuddy()
				.with(ClassGeneratorHelper.TypeNamingStrategy.INSTANCE)
				.subclass(parameterizedType(parentClass, context.sourceType, context.targetType).build(), ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.defineMethod("toString", String.class, Visibility.PUBLIC)
					.intercept(new Implementation.Simple(new TextConstant(format("converter %s to %s", context.sourceType, context.targetType)), MethodReturn.REFERENCE));
		return builder;
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
	private static <T> Builder<T> generateInitCustomConverters(GeneratorFactory.Context<?,?> context, Builder<T> builder) {
		List<Implementation> initConverters = new ArrayList<>();
		int index = 0;

		for (MappedProperties mappedProperties : Iterables.filter(context.classMap.getMappedProperties(), filterOnlyCustomConverters())) {
			final Generic build = parameterizedType(INTERNAL_CONVERTER_TYPE, mappedProperties.getSourceType(), mappedProperties.getTargetType()).build();
			final String fieldName = CONVERTER_FIELDNAME_PREFIX + (index++);
			final FieldDescription.Token token = new FieldDescription.Token(fieldName, Opcodes.ACC_PRIVATE, build);
			
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
	 * Generate the method <code>public void convert(source, target){...}</code>
	 * @param context
	 * @param builder
	 * @return
	 */
	private static Builder<Convert<?, ?>> generate2ArgsConvertMethod(Context<?,?> context, Builder<Convert<?, ?>> builder) {
		builder = builder.defineMethod(CONVERT_METHODNAME, Void.TYPE, Visibility.PUBLIC)
			.withParameter(context.sourceType)
			.withParameter(context.targetType)
			.intercept( new ClassGeneratorHelper.NullcheckAndReturnBuilder(false).andThen(new Converter2Builder(context)) );
		return builder;
	}


	/**
	 * Generate the method <code>public target convert(source){... return target; }</code>
	 * @param builder
	 * @return
	 */
	private static Builder<Convert<?, ?>> generateConvertMethod(Builder<Convert<?, ?>> builder) {
		builder = builder.method( isDeclaredBy(parentClass).and(named(CONVERT_METHODNAME)) )
				.intercept( new ClassGeneratorHelper.NullcheckAndReturnBuilder().andThen(new Converter1Builder()) );
		return builder;
	}


	/**
	 * Create a signature for the field of a custom converter.
	 * <pre>Converter<String,Integer> field</pre>
	 * returns
	 * <pre>Lcom/google/common/base/Converter<Ljava/lang/String;Ljava/lang/Integer;>;</pre>
	 * @param mappedProperties
	 * @return
	 */
	static class FieldInitializer implements Implementation {
		private FieldDescription.Token field;
		private Class<?> customConverter;
		
		public FieldInitializer(FieldDescription.Token field, Class<?> customConverter) {
			this.field = field;
			this.customConverter = customConverter;
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
					BeanCreationStrategyinvokeDynamic.dynamic("newInstance", new TypeDescription.ForLoadedType(customConverter), Collections.<TypeDescription>emptyList(), emptyList()),
					TypeCasting.to(field.getType()),
					FieldAccess.forField( new Latent(implementationTarget.getInstrumentedType(), field) ).write()
			).appender(implementationTarget);
		}
	}
	

	/**
	 * Create the default constructor that calls the <code>initCustomConverters()</code> method.
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
	private static enum ConstructorBuilder implements Implementation {
		INSTANCE;
		
		@Override
		public InstrumentedType prepare(InstrumentedType instrumentedType) {
			return instrumentedType;
		}
		
		@Override
		public ByteCodeAppender appender(Target implementationTarget) {
			return new Implementation.Simple(
					MethodVariableAccess.loadThis(),
					MethodInvocation.invoke(implementationTarget.getInstrumentedType().getDeclaredMethods().filter( ElementMatchers.named(INIT_CUSTOM_CONVERTERS_METHOD) ).getOnly()),
					MethodReturn.VOID
			).appender(implementationTarget);
		}
	}


	/**
	 * Generate the method implementation for <code>public target convert(source)</code>
	 * <code>
	 * 		T target = newInstance(T.class);
	 *      this.convert(source, target);
	 *      return target;
	 * </code>
	 */
	private static class Converter1Builder implements ByteCodeAppender, Implementation {
		@Override
		public InstrumentedType prepare(InstrumentedType instrumentedType) {
			return instrumentedType;
		}
		
		@Override
		public ByteCodeAppender appender(Target implementationTarget) {
			return this;
		}
		
		@Override
		public ByteCodeAppender.Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
			final ParameterDescription parameterDescription = instrumentedMethod.getParameters().get(0);			
			final TypeDescription returnType = 
					new TypeDescription.Latent(instrumentedMethod.getReturnType().getTypeName(), instrumentedMethod.getReturnType().getModifiers(), instrumentedMethod.getReturnType(), instrumentedMethod.getReturnType().getInterfaces());
			final MethodVariableAccess target_var = MethodVariableAccess.of(returnType);
			final int local_field_offset = parameterDescription.getOffset() + 1;
			final List<StackManipulation> insr = new ArrayList<>(8);
			
			// new instance
			insr.add(BeanCreationStrategyinvokeDynamic.dynamic("new_instance", returnType, Collections.<TypeDescription>emptyList(), emptyList()));
			insr.add(target_var.storeAt(local_field_offset));
			
			// this.convert(source, target)
			insr.add(MethodVariableAccess.loadThis());
			insr.add(MethodVariableAccess.load(parameterDescription));
			insr.add(target_var.loadFrom(local_field_offset));
			insr.add(MethodInvocation.invoke( implementationContext.getInstrumentedType().getDeclaredMethods().filter( named(CONVERT_METHODNAME).and(takesArguments(2)) ).getOnly() ));
			
			// return target
			insr.add(target_var.loadFrom(local_field_offset));
			insr.add(MethodReturn.of(returnType));
			
			StackManipulation.Size size = new StackManipulation.Compound(insr).apply(methodVisitor, implementationContext);
			return new ByteCodeAppender.Size(size.getMaximalSize()+1, instrumentedMethod.getStackSize()+1);
		}
	}



	private static class Converter2Builder implements ByteCodeAppender, Implementation {
		private GeneratorFactory.Context<?,?> context;

		public Converter2Builder(GeneratorFactory.Context<?,?> context) {
			this.context = context;
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
				StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target, GeneratorFactory.Context<?,?> context, Implementation.Context implementationContext){
					final MethodDescription.ForLoadedMethod sourceMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getSourceProperty().getReadMethod());
					final MethodDescription.ForLoadedMethod targetMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getTargetProperty().getWriteMethod());
					final TypeDescription targetType = targetMethodDesc.getParameters().get(0).getType().asErasure();
					
					LOG.debug("convert: read={} ,write={}", fieldMap.getSourceProperty().getReadMethod(), fieldMap.getTargetProperty().getWriteMethod());

					return new StackManipulation.Compound(
							MethodVariableAccess.load(target),
							MethodVariableAccess.load(source),
							MethodInvocation.invoke( sourceMethodDesc ),
							MappingLocatorInvokeDynamic.dynamic(CONVERT_METHODNAME, targetType, asList(sourceMethodDesc.getReturnType().asErasure()), emptyList()),
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
				StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target, GeneratorFactory.Context<?,?> context, Implementation.Context implementationContext) {
					final MethodDescription.ForLoadedMethod sourceMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getSourceProperty().getReadMethod());
					final MethodDescription.ForLoadedMethod targetMethodDesc = new MethodDescription.ForLoadedMethod(fieldMap.getTargetProperty().getWriteMethod());
					final FieldDescription.Token field = context.customConverters.get(fieldMap.getCustomConverter()).field;
					final Generic targetType = targetMethodDesc.getParameters().get(0).getType();
					final TypeDescription wrapperType = ClassGeneratorHelper.getPrimitiveWrapperType(targetType); // if target is primitive, use wrapper
					final List<StackManipulation> insr = new ArrayList<>(9);
					
					LOG.debug("convert: read={} ,write={}, converter={} (primitive:{})", fieldMap.getSourceProperty().getReadMethod(), fieldMap.getTargetProperty().getWriteMethod(), fieldMap.getCustomConverter(), wrapperType!=null);
					
					insr.add(MethodVariableAccess.load(target));
					insr.add(MethodVariableAccess.loadThis());
					insr.add(FieldAccess.forField(new FieldDescription.Latent(implementationContext.getInstrumentedType().asErasure(), field)).read());
					insr.add(MethodVariableAccess.load(source));
					insr.add(MethodInvocation.invoke(sourceMethodDesc));
					insr.add(ConverterMethodInvoke);
					if( wrapperType != null ){
						insr.add( TypeCasting.to(wrapperType) );
						insr.add( PrimitiveUnboxingDelegate.forReferenceType(wrapperType).assignUnboxedTo(targetType, null, null) );
					}else{
						insr.add( TypeCasting.to(targetType.asErasure()) );							
					}
					insr.add(MethodInvocation.invoke(targetMethodDesc));
					
					return new StackManipulation.Compound(insr);
				}
			};
			

			abstract StackManipulation getCode(MappedProperties fieldMap, ParameterDescription source, ParameterDescription target, GeneratorFactory.Context<?,?> context, Implementation.Context implementationContext);
		}
		
		
		@Override
		public ByteCodeAppender.Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
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
			StackManipulation.Size size = new StackManipulation.Compound(insr).apply(methodVisitor, implementationContext);
			return new ByteCodeAppender.Size(size.getMaximalSize()+incr, instrumentedMethod.getStackSize()+incr);
		}

	}

}
