package org.spee.commons.convert.generator;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.parameterizedType;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.spee.commons.convert.generator.ClassGeneratorHelper.filterOnlyCustomConverters;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.Convert;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;
import org.spee.commons.convert.internals.MappingLocator;
import org.spee.commons.utils.ReflectionUtils;

import com.google.common.base.Converter;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldDescription.Latent;
import net.bytebuddy.description.field.FieldDescription.Token;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
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

/**
 * Generating the converter. It looks like: <code>
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

	public void convertField_0(Source source, Target target){
		target.setValue0( convert(source.getValue0(), TargetValue0.class) );
	}
	
	public void convertField_1(Source source, Target target){
		target.setValue1( (TargetType)converter_1.convert(source.getValue1()) );
	}

	public void convert(Source source, Target target){
		convertField_0(source, target);
		convertField_1(source, target);
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
	private static final String CONVERTFIELD_METHODNAME_PREFIX = "convertField_";
	protected static final String CONVERT_METHODNAME = "convert";
	private static final Class<Converter> INTERNAL_CONVERTER_TYPE = Converter.class;
	private static final Optional<Class> JAVAX_ANNOTATION_GENERATED = ReflectionUtils.isTypePresent("javax.annotation.Generated");

	private static final Logger LOG = LoggerFactory.getLogger(GeneratorFactory.class);
	/**
	 * Parent class for generated converters
	 */
	@SuppressWarnings("rawtypes")
	private static final Class<Convert> PARENT_CLASS = Convert.class;

	protected static WithImplicitInvocationTargetType BeanCreationStrategyinvokeDynamic;
	protected static WithImplicitInvocationTargetType MappingLocatorInvokeDynamic;
	protected static WithImplicitInvocationTargetType ConverterMethodInvoke;

	static {
		try {
			// invoke dynamic BootstrapMethod (BSM) for converters and constructors
			BeanCreationStrategyinvokeDynamic = MethodInvocation.invoke(new MethodDescription.ForLoadedMethod(BeanCreationStrategy.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class)));
			MappingLocatorInvokeDynamic = MethodInvocation.invoke(new MethodDescription.ForLoadedMethod(MappingLocator.class.getDeclaredMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class)));
			// Direct method invoker on converter
			ConverterMethodInvoke = MethodInvocation.invoke(new MethodDescription.ForLoadedMethod(INTERNAL_CONVERTER_TYPE.getMethod(CONVERT_METHODNAME, Object.class)));
		} catch (NoSuchMethodException | SecurityException e) {
			// we are sure of their existence, since they are bundled
		}
	}

	protected static class Context<S, T> {
		String className;
		String internalClassName;
		Type sourceType;
		Type targetType;
		ClassMap classMap = new ClassMap(null, null);
		Map<Class<?>, ConverterField> customConverters = new LinkedHashMap<>();
		Map<String, MappedProperties> fieldConvert = new LinkedHashMap<>();

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
	public Class<Convert<?, ?>> build(ClassMap classMap) {
		Context context = new Context<>(classMap.getSource().getBeanDescriptor().getBeanClass(), classMap.getTarget().getBeanDescriptor().getBeanClass());
		context.classMap = classMap;
		return build(context);
	}


	public <S, T> Class<Convert<S, T>> build(Class<S> sourceClass, Class<T> targetClass) {
		Context<S, T> context = new Context<>(sourceClass, targetClass);
		context.classMap = ClassMapBuilder.build(sourceClass, targetClass).useDefaults(true).generate();
		return build(context);
	}


	@SuppressWarnings("unchecked")
	private static <S, T> Class<Convert<S, T>> build(Context<S, T> context) {
		Builder<Convert<?, ?>> builder = generateClassDefinition(context)
											.defineConstructor(Visibility.PUBLIC)
											.intercept(SuperMethodCall.INSTANCE.andThen(ConstructorBuilder.INSTANCE));
		builder = generateInitCustomConverters(context, builder);
		builder = generateConvertFieldMethods(context, builder);
		builder = generate2ArgsConvertMethod(context, builder);
		builder = generateConvertMethod(builder);
		

		Loaded<Convert<?, ?>> loaded = builder.make().load(GeneratorFactory.class.getClassLoader());
		Path saveTo = Paths.get("target/", loaded.getTypeDescription().getName() + ".class");
		try {
			LOG.debug("Writing class bytes to file: {}", saveTo);
			Files.write(saveTo, loaded.getBytes());
		} catch (IOException e) {
			LOG.warn("Could not write class bytes to file {}: {}", saveTo, e.getMessage(), e);
		}

		return (Class<Convert<S, T>>) loaded.getLoaded();
	}


	/**
	 * Generate the basic class definition
	 * 
	 * <pre>
	 * package a.b.c;
	 * 
	 * &#64;Generated(value="GeneratorFactory", date="now") // if type is present
	 * public class gen[Base64 A to B] extends Convert<A,B> {
	 *     
	 *     public String toString(){
	 *         return "converter A to B";
	 *     }
	 * }
	 * </pre>
	 * 
	 * @param context
	 * @return
	 */
	private static Builder<Convert<?, ?>> generateClassDefinition(Context<?, ?> context) {
		@SuppressWarnings("unchecked")
		Builder<Convert<?, ?>> builder = (Builder<Convert<?, ?>>) new ByteBuddy().with(ClassGeneratorHelper.TypeNamingStrategy.INSTANCE)
				.subclass(parameterizedType(PARENT_CLASS, context.sourceType, context.targetType).build(), ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.defineMethod("toString", String.class, Visibility.PUBLIC).intercept(new Implementation.Simple(
						new TextConstant(format("converter %s to %s", context.sourceType, context.targetType)), MethodReturn.REFERENCE));

		if (JAVAX_ANNOTATION_GENERATED.isPresent()) {
			builder = builder.annotateType(AnnotationDescription.Builder.ofType(JAVAX_ANNOTATION_GENERATED.get())
							.define("value", TypeDescription.ArrayProjection.of(TypeDescription.STRING), GeneratorFactory.class.getName())
							.define("date", DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL).format(new Date()))
							.build());
		}
		return builder;
	}


	/**
	 * Generate a private field per custom converter and a method to initialize
	 * them. This method is called by the constructor. The
	 * <code>newInstance</code> is a dynamic call to retrieve the custom
	 * converter.
	 * 
	 * <pre>
	 * private Converter converter_0;
	 * private Converter converter_1;
	 * 
	 * private void initCustomConverters(){
	 *     converter_0 = newInstance([customconverter.class]);
	 *     converter_1 = newInstance([anothercustomconverter.class]);
	 * }
	 * </pre>
	 * 
	 * @param cw
	 * @param context
	 */
	private static <T> Builder<T> generateInitCustomConverters(GeneratorFactory.Context<?, ?> context, Builder<T> builder) {
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
	 * 
	 * @param context
	 * @param builder
	 * @return
	 */
	private static Builder<Convert<?, ?>> generate2ArgsConvertMethod(Context<?, ?> context, Builder<Convert<?, ?>> builder) {
		builder = builder.defineMethod(CONVERT_METHODNAME, Void.TYPE, Visibility.PUBLIC).withParameter(context.sourceType).withParameter(context.targetType)
				.intercept(new ClassGeneratorHelper.NullcheckAndReturnBuilder(false).andThen(new Converter2Builder(context)));
		return builder;
	}


	/**
	 * Generate the method
	 * <code>public target convert(source){... return target; }</code>
	 * 
	 * @param builder
	 * @return
	 */
	private static Builder<Convert<?, ?>> generateConvertMethod(Builder<Convert<?, ?>> builder) {
		builder = builder.method(isDeclaredBy(PARENT_CLASS).and(named(CONVERT_METHODNAME)))
				.intercept(new ClassGeneratorHelper.NullcheckAndReturnBuilder().andThen(new Converter1Builder()));
		return builder;
	}


	/**
	 * Generate the methods to convert every field
	 * @param context
	 * @param builder
	 * @return
	 */
	private static Builder<Convert<?, ?>> generateConvertFieldMethods(Context<?, ?> context, Builder<Convert<?, ?>> builder) {
		int convertFieldMethodIndex = 0;
		for (MappedProperties fieldMap : context.classMap.getMappedProperties()) {
			final String fieldMethodName = CONVERTFIELD_METHODNAME_PREFIX + (convertFieldMethodIndex++);

			if( fieldMap.hasCustomConverter() ){
				builder = new GenerateConvertMethod_WithFieldUsage(context, fieldMethodName, fieldMap).generateConvertMethod(builder);
			}
			else {
				builder = new GenerateConvertMethod_Direct(context, fieldMethodName, fieldMap).generateConvertMethod(builder);
			}
		}
		
		return builder;
	}

	
	/**
	 * Create a signature for the field of a custom converter.
	 * 
	 * <pre>
	 * Converter<String,Integer> field
	 * </pre>
	 * 
	 * returns
	 * 
	 * <pre>
	 * Lcom/google/common/base/Converter<Ljava/lang/String;Ljava/lang/Integer;>;
	 * </pre>
	 * 
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
						BeanCreationStrategyinvokeDynamic.dynamic("newInstance", new TypeDescription.ForLoadedType(customConverter),
						Collections.<TypeDescription> emptyList(), emptyList()),
						TypeCasting.to(field.getType()),
						FieldAccess.forField(new Latent(implementationTarget.getInstrumentedType(), field)).write()
					).appender(implementationTarget);
		}
	}

	/**
	 * Create the default constructor that calls the
	 * <code>initCustomConverters()</code> method. <code>
	 * public Classname(){
	 * 	 super();
	 *   initCustomConverters();
	 * }
	 * </code>
	 * 
	 * @param cw
	 * @param PARENT_CLASS
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
						MethodInvocation.invoke(implementationTarget.getInstrumentedType().getDeclaredMethods().filter(ElementMatchers.named(INIT_CUSTOM_CONVERTERS_METHOD)).getOnly()),
						MethodReturn.VOID
					).appender(implementationTarget);
		}
	}

	/**
	 * Generate the method implementation for
	 * <code>public target convert(source)</code> <code>
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
			final TypeDescription returnType = new TypeDescription.Latent(instrumentedMethod.getReturnType().getTypeName(),
																		instrumentedMethod.getReturnType().getModifiers(), 
																		instrumentedMethod.getReturnType(), 
																		instrumentedMethod.getReturnType().getInterfaces());
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
			insr.add(MethodInvocation.invoke(
					implementationContext.getInstrumentedType().getDeclaredMethods().filter(named(CONVERT_METHODNAME).and(takesArguments(2))).getOnly()));

			// return target
			insr.add(target_var.loadFrom(local_field_offset));
			insr.add(MethodReturn.of(returnType));

			StackManipulation.Size size = new StackManipulation.Compound(insr)
															.apply(methodVisitor, implementationContext);
			return new ByteCodeAppender.Size(size.getMaximalSize() + 1, instrumentedMethod.getStackSize() + 1);
		}
	}

	/**
	 * Generate the method implementation for
	 * <code>public target convert(source,target)</code> <code>
	 * 		this.convertField_0(source, target);
	 * 		this.convertField_1(source, target);
	 * 		this.convertField_n(source, target);
	 *      return target;
	 * </code>
	 */
	private static class Converter2Builder implements ByteCodeAppender, Implementation {
		private GeneratorFactory.Context<?, ?> context;


		public Converter2Builder(GeneratorFactory.Context<?, ?> context) {
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

		/**
		 * <code>this.convertField_0(source, target);</code>
		 */
		@Override
		public ByteCodeAppender.Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
			ParameterDescription source = instrumentedMethod.getParameters().get(0);
			ParameterDescription target = instrumentedMethod.getParameters().get(1);
			List<StackManipulation> insr = new ArrayList<>();

			MethodList<InDefinedShape> convertFieldMethodList = implementationContext.getInstrumentedType().getDeclaredMethods()
																	.filter(nameStartsWith(CONVERTFIELD_METHODNAME_PREFIX).and(takesArguments(2)));

			for (InDefinedShape convertFieldMethod : convertFieldMethodList) {
				LOG.debug("Mapping calls method {}", convertFieldMethod);
				insr.add(
						new StackManipulation.Compound(
								MethodVariableAccess.loadThis(),
								MethodVariableAccess.load(source),
								MethodVariableAccess.load(target),
								MethodInvocation.invoke(convertFieldMethod)
						)
				);
			}

			insr.add(MethodReturn.VOID);
			StackManipulation.Size size = new StackManipulation.Compound(insr).apply(methodVisitor, implementationContext);
			return new ByteCodeAppender.Size(size.getMaximalSize(), instrumentedMethod.getStackSize());
		}

	}

}
