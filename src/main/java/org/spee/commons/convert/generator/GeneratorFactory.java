package org.spee.commons.convert.generator;


import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_7;
import static org.objectweb.asm.Type.VOID_TYPE;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;
import static org.spee.commons.convert.generator.ClassGeneratorHelper.filterOnlyCustomConverters;
import static org.spee.commons.convert.generator.ClassGeneratorHelper.nullCheckWithReturn;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;
import org.spee.commons.convert.generator.GeneratorFactory.Context.ConverterField;
import org.spee.commons.convert.internals.IterableMappingLocator;
import org.spee.commons.convert.internals.MappingLocator;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.primitives.Primitives;

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
	private static final String CONSTRUCTOR_INIT = "<init>";
	private static final String CONVERTER_FIELDNAME_PREFIX = "converter_";
	private static final String VOIDMETHOD = getMethodDescriptor(VOID_TYPE); // ()V
	private static final Type converterType = getType(com.google.common.base.Converter.class);
	private static final Type objectType = getType(Object.class);
	/**
	 * In instance methods (so non-static), the index for <code>this</code> is 0.
	 */
	private static final int REFINSTANCE_THIS = 0;
	/**
	 * In instance methods, the index for the first argument is 1.
	 */
	private static final int REFINSTANCE_ARGUMENT_1 = 1;
	/**
	 * In instance methods, the index for the second argument is 2.
	 */
	private static final int REFINSTANCE_ARGUMENT_2 = 2;

	private final Logger logger = LoggerFactory.getLogger(GeneratorFactory.class);
	/**
	 * Bootstrap method for invokedynamic converters
	 */
	private final Handle converterBootstrapMethod;

	private final Handle listBootstrapMethod;
	/**
	 * Bootstrap method for invokedynamic creating new instances
	 */
	private final Handle newinstanceBootstrapMethod;
	/**
	 * Parent class for generated converters
	 */
	private final Class<?> parentClass = Object.class;

	
	static class Context {
		String className;
		String internalClassName;
		Type sourceType;
		Type targetType;
		ClassMap classMap;
		Map<Class<?>, ConverterField> customConverters = new LinkedHashMap<>();
		
		static class ConverterField {
			String fieldName;
			String signature;
		}
	}
	

	public GeneratorFactory() {
		// invoke dynamic BootstrapMethod (BSM) for converters and constructors
		final String mappingLocatorClassName = getInternalName(MappingLocator.class);
		final String mappingLocatorBSMName = "bootstrap";
		final String mappingLocatorBSMType = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class, String.class).toMethodDescriptorString();
		converterBootstrapMethod = new Handle(H_INVOKESTATIC, mappingLocatorClassName, mappingLocatorBSMName, mappingLocatorBSMType, false);
		
		final String beanCreationStrategyClassName = getInternalName(BeanCreationStrategy.class);
		final String beanCreationStrategyBSMName = "bootstrap";
		final String beanCreationStrategyBSMType = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class).toMethodDescriptorString();
		newinstanceBootstrapMethod = new Handle(H_INVOKESTATIC, beanCreationStrategyClassName, beanCreationStrategyBSMName, beanCreationStrategyBSMType, false);
		
		final String iterableMappingLocatorClassName = getInternalName(IterableMappingLocator.class);
		final String iterableMappingLocatorBSMName = "bootstrap";
		final String iterableMappingLocatorBSMType = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class).toMethodDescriptorString();
		listBootstrapMethod = new Handle(H_INVOKESTATIC, iterableMappingLocatorClassName, iterableMappingLocatorBSMName, iterableMappingLocatorBSMType, false);
	}

	
	public Class<?> build(ClassMap classMap){
		Context context = new Context();
		context.sourceType = getType(classMap.getSource().getBeanDescriptor().getBeanClass());
		context.targetType = getType(classMap.getTarget().getBeanDescriptor().getBeanClass());
		context.classMap = classMap;

		return build(context);
	}

	
	public Class<?> build(Class<?> sourceClass, Class<?> targetClass){
		Context context = new Context();
		context.sourceType = getType(sourceClass);
		context.targetType = getType(targetClass);
		context.classMap = ClassMapBuilder.build(sourceClass, targetClass).useDefaults().generate();
		
		return build(context);
	}
	
	
	private Class<?> build(Context context){
		prepareContext(context);
		
		ClassWriter cw = new ClassWriter(COMPUTE_MAXS);
		
		generateClassDefinition(cw, context);
		generateConstructor(cw, parentClass, context);
		generateInitCustomConverters(cw, context);
		generate2ArgsConvertMethod(cw, context);
		generateConvertMethod(cw, context);
		cw.visitEnd();

		saveClass(cw, context); // TODO save .class file
		Class<?> generatedClass = loadClass(cw, context); // TODO load class
		// TODO return MethodHandle
		return generatedClass;
	}


	/**
	 * Generate a classname and fieldnames for the custom converters.
	 * @param context
	 */
	private void prepareContext(Context context) {
		final String className = "gen_" + context.sourceType.getInternalName().replace('.', '_').replace('/', '_') + "to" + 
									context.targetType.getInternalName().replace('.', '_').replace('/', '_') + "Converter";
		context.className = "generated.converters." + className;
		context.internalClassName = context.className.replace('.', '/');

		int converterIndex = 0;
		for (MappedProperties mappedProperties : Iterables.filter(context.classMap.getMappedProperties(), filterOnlyCustomConverters())) {
			ConverterField converterField = new ConverterField();
			converterField.fieldName = CONVERTER_FIELDNAME_PREFIX + (converterIndex++);
			converterField.signature = createFieldSignature(mappedProperties);
			context.customConverters.put(mappedProperties.customConverter, converterField);
		}
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
	private String createFieldSignature(MappedProperties mappedProperties) {
		SignatureWriter signature = new SignatureWriter();
		signature.visitClassType(converterType.getInternalName());
		signature.visitTypeArgument(SignatureVisitor.INSTANCEOF);
		signature.visitClassType(getInternalName(mappedProperties.sourceProperty.getReadMethod().getReturnType()));
		signature.visitEnd();
		signature.visitTypeArgument(SignatureVisitor.INSTANCEOF);
		signature.visitClassType(getInternalName(Primitives.wrap(mappedProperties.targetProperty.getWriteMethod().getParameterTypes()[0])));
		signature.visitEnd();
		signature.visitEnd();
		return signature.toString();
	}
	
	/**
	 * 
	 * @param returnType
	 * @param parameterType
	 * @return
	 */
	private String createMethodDescriptor(java.lang.reflect.Type returnType, java.lang.reflect.Type parameterType){
//		()V
//		(Ljava/util/Map;)Ljava/util/List;
//		(Ljava/util/Map<Ljava/lang/String;Lorg/spee/commons/beans/convert/Address;>;)Ljava/util/List<Lorg/spee/commons/beans/convert/Address;>;
		
		SignatureWriter signature = new SignatureWriter();
		signature.visitParameterType(); // (
		
		if( parameterType instanceof ParameterizedType ){
			ParameterizedType classTypes = (ParameterizedType)parameterType;
			
			signature.visitClassType(Type.getInternalName((Class<?>)classTypes.getRawType()));
			
			for (java.lang.reflect.Type type : classTypes.getActualTypeArguments()) {
				signature.visitTypeArgument(SignatureVisitor.INSTANCEOF);
				signature.visitClassType(getInternalName((Class<?>)type));
				signature.visitEnd();
			}
			signature.visitEnd();
		}else if (parameterType != null){
			signature.visitClassType(getInternalName((Class<?>)parameterType)); // Ljava/lang/String			
			signature.visitEnd();
		}

		signature.visitReturnType();
		
		if( returnType instanceof ParameterizedType ){
			ParameterizedType classTypes = (ParameterizedType)returnType;
			
			signature.visitClassType(getInternalName((Class<?>)classTypes.getRawType()));
			
			for (java.lang.reflect.Type type : classTypes.getActualTypeArguments()) {
				signature.visitTypeArgument(SignatureVisitor.INSTANCEOF);
				signature.visitClassType(getInternalName((Class<?>)type));
				signature.visitEnd();
			}
			signature.visitEnd();

		}else if( returnType == null ){
			signature.visitBaseType('V'); // Type.VOID_TYPE
		}else{
			signature.visitClassType(getType((Class<?>)returnType).getInternalName());
			signature.visitEnd();
		}

		return signature.toString();
	}
	

	/**
	 * Generate the class definition
	 * <pre>public class internalClassName extends parentClass {}</pre>
	 * @param cw
	 * @param context
	 */
	private void generateClassDefinition(ClassWriter cw, Context context) {
		String signature = null;
		String[] implementedInterfaces = null; // new String[]{Type.getInternalName(Converter.class)}
		cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, context.internalClassName, signature, getInternalName(parentClass), implementedInterfaces);
	}
	

	/**
	 * Write the class to file.
	 * @param cw
	 * @param context
	 */
	private void saveClass(ClassWriter cw, Context context){
		byte[] byteClass = cw.toByteArray();
		String className = context.internalClassName;
		try {
			File outputClassFile = new File(className + ".class");
			outputClassFile.getParentFile().mkdirs();

			logger.debug("Writing class '{}' to file {}", context.className, outputClassFile);
			Files.write(byteClass, outputClassFile);
		} catch (IOException e) {
			logger.warn("Could not write class to file", e);
		}
	}

	
	private Class<?> loadClass(ClassWriter cw, Context context){
		//override classDefine (as it is protected) and define the class.
	    Class<?> clazz = null;
	    final byte[] b = cw.toByteArray();
	    try {
	      ClassLoader loader = ClassLoader.getSystemClassLoader();
	      Class<?> cls = Class.forName("java.lang.ClassLoader");
	      java.lang.reflect.Method method = cls.getDeclaredMethod("defineClass", new Class[] { String.class, byte[].class, int.class, int.class });

	      // protected method invocaton
	      method.setAccessible(true);
	      try {
	        Object[] args = new Object[] { context.className, b, new Integer(0), new Integer(b.length)};
	        clazz = (Class<?>) method.invoke(loader, args);
	      } finally {
	        method.setAccessible(false);
	      }
	    } catch (Exception e) {
	    	logger.error("Could not load generated class {}", context.className, e);
	      throw new RuntimeException(e);
	    }
	    return clazz;
	}
	
	
	/**
	 * Create the default constructor.
	 * If there are custom converters, a call to the initialize method is also added.
	 * @param cw
	 * @param parentClass
	 * @param context
	 */
	private void generateConstructor(ClassWriter cw, Class<?> parentClass, Context context) {
		/*
		 * public Classname(){
		 * 	 super();
		 *   initCustomConverters(); // if present optional
		 * }
		 */
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, CONSTRUCTOR_INIT, VOIDMETHOD, null, null);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, REFINSTANCE_THIS);
		mv.visitMethodInsn(INVOKESPECIAL, getInternalName(parentClass), CONSTRUCTOR_INIT, VOIDMETHOD, false);
		if( !context.customConverters.isEmpty() ){
			mv.visitVarInsn(ALOAD, REFINSTANCE_THIS);
			mv.visitMethodInsn(INVOKESPECIAL, context.internalClassName, INIT_CUSTOM_CONVERTERS_METHOD, VOIDMETHOD, false);
		}
		mv.visitInsn(RETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();
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
	private void generateInitCustomConverters(ClassWriter cw, Context context){
		if( context.customConverters.isEmpty() ) return;
		
		// generate fields
		for(Entry<Class<?>, ConverterField> converter : context.customConverters.entrySet()){
			// private Converter converter_?;
			final ConverterField field = converter.getValue();
			cw.visitField(ACC_PRIVATE, field.fieldName, converterType.getDescriptor(), field.signature, null).visitEnd();
		}
		
		// generate init method for custom converters
		MethodVisitor mv = cw.visitMethod(ACC_PRIVATE, INIT_CUSTOM_CONVERTERS_METHOD, VOIDMETHOD, null, null);
		mv.visitCode();

		for(Entry<Class<?>, ConverterField> entry : context.customConverters.entrySet()){
			// converter_? = newInstance(customconverter?.class);
			mv.visitVarInsn(ALOAD, REFINSTANCE_THIS);
			mv.visitInvokeDynamicInsn("newInstance", getMethodDescriptor(getType(entry.getKey())), newinstanceBootstrapMethod);
			mv.visitFieldInsn(PUTFIELD, context.internalClassName, entry.getValue().fieldName, converterType.getDescriptor());
		}
		
		mv.visitMaxs(context.customConverters.size()+2, context.customConverters.size()+2);
		mv.visitInsn(RETURN);
		mv.visitEnd();
	}
	

	/**
	 * Generate the method <code>public void convert(source, target)</code>
	 * @param cw
	 * @param sourceType
	 * @param targetType
	 */
	private void generate2ArgsConvertMethod(ClassWriter cw, Context context) {
		/*
		 * public void convert(source, target){
		 * 		target.setField1( convert(source.getField1()) );
		 * 		target.setField2( converter_0(source.getField2()) );
		 * }
		 */
		
		MethodVisitor mv;
		{
			mv = cw.visitMethod(ACC_PUBLIC, "convert", getMethodDescriptor(VOID_TYPE, context.sourceType, context.targetType), null, null);
			mv.visitCode();
			
			for(MappedProperties fieldMap : context.classMap.getMappedProperties()){
				if( fieldMap.customConverter == null ) {
					if( fieldMap.sourceProperty.getReadMethod().getReturnType().isAssignableFrom(Collection.class) ||
						fieldMap.sourceProperty.getReadMethod().getReturnType().isAssignableFrom(Map.class) ||
						fieldMap.sourceProperty.getReadMethod().getReturnType().isAssignableFrom(Iterable.class) ){
						generateCollectionFieldMapping(cw, mv, context, fieldMap);
					}else{
						generateSimpleFieldMapping(cw, mv, context, fieldMap);
					}
				}else{
					generateLocalFieldconverterFieldMapping(context, mv, fieldMap);
				}
			}
			
				// this.extendedmapping_name(source, target);
//				mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_2);
//				mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_1);
//				mv.visitMethodInsn(INVOKEVIRTUAL, context.internalClassName, "extendedmapping_name", getMethodDescriptor(VOID_TYPE, context.sourceType, context.targetType), false);

			mv.visitMaxs(context.classMap.getMappedProperties().size()+10, context.classMap.getMappedProperties().size()+10);
			mv.visitInsn(RETURN);
			mv.visitEnd();
		}
	}


	/**
	 * Generate the method <code>public target convert(source)</code>
	 * 
	 * @param cw
	 * @param converterBootstrapMethod
	 * @param sourceType
	 * @param targetType
	 */
	private void generateConvertMethod(ClassWriter cw, Context context) {
		/*
		 * public T convert(S source){
		 * 		if( source == null ) return null;
		 * 		T target = newInstance(T.class);
		 *      this.convert(source, target);
		 *      return target;
		 * }
		 */
		final int LOCAL_VAR_1 = 2; // this + parameters.size() + 1
		final String[] exceptions = {getType(InstantiationException.class).getInternalName(), getType(IllegalAccessException.class).getInternalName()};
		
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "convert", getMethodDescriptor(context.targetType, context.sourceType), null, exceptions);
		mv.visitCode();
		// if null then return
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
		mv.visitMaxs(8, 3);
		mv.visitEnd();
	}

	
	/**
	 * Write bytecode for a single property conversion.
	 * The convert call is done by InvokeDynamic. It looks somewhat like the following:
	 * <pre>
	 * target.setValue( convert(source.getValue()) );
	 * </pre>
	 * 
	 * @param mv MethodVisitor to add to
	 * @param sourceType Type of the source (class)
	 * @param sourceGetter The actual get method
	 * @param targetType Type of the target (class)
	 * @param targetSetter The actual set method
	 * @throws IllegalArgumentException if getter or setter is <code>null</code>
	 */
	private void generateSimpleFieldMapping(ClassWriter cw, MethodVisitor mv, Context context, MappedProperties fieldMap){
		final Method sourceGetter = fieldMap.sourceProperty.getReadMethod();
		final Method targetSetter = fieldMap.targetProperty.getWriteMethod();
		final String convertMethodDescriptor = getMethodDescriptor(getType(targetSetter.getParameterTypes()[0]), getType(sourceGetter.getReturnType()));
		
		String genericMethodDescriptor = createMethodDescriptor(targetSetter.getGenericParameterTypes()[0], sourceGetter.getGenericReturnType());
		if( convertMethodDescriptor.equals(genericMethodDescriptor) ){
			genericMethodDescriptor = ""; // No generics, so no specific signature needed
		}
		
		mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_2);
		mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_1);
		mv.visitMethodInsn(INVOKEVIRTUAL, context.sourceType.getDescriptor(), sourceGetter.getName(), getMethodDescriptor(sourceGetter), false); // source.get()
		mv.visitInvokeDynamicInsn("convert", convertMethodDescriptor, converterBootstrapMethod, genericMethodDescriptor);
		mv.visitMethodInsn(INVOKEVIRTUAL, context.targetType.getDescriptor(), targetSetter.getName(), getMethodDescriptor(targetSetter), false); // target.set()
	}

	
	private Type getGenericType(java.lang.reflect.Type type){
//		(Ljava/util/Map<Ljava/lang/String;Lorg/spee/commons/beans/convert/Address;>;)Ljava/util/List<Lorg/spee/commons/beans/convert/Address;>;
		
		if( type instanceof ParameterizedType ){
			ParameterizedType classTypes = (ParameterizedType)type;
			if( ((Class<?>)classTypes.getRawType()).isAssignableFrom(Map.class) ){
				return getType(Map.Entry.class);
			}
			
			return getType((Class<?>)classTypes.getActualTypeArguments()[0]);
		}else {
			return getType((Class<?>)type);
		}
	}
	
	
	private void generateCollectionFieldMapping(ClassWriter cw, MethodVisitor mv, Context context, MappedProperties fieldMap){
		final Method sourceGetter = fieldMap.sourceProperty.getReadMethod(); // Map<K,V> getPlaces()
		final Method targetSetter = fieldMap.targetProperty.getWriteMethod(); // void setAddress( List<T> )
		final String convertMethodDescriptor = getMethodDescriptor(getGenericType(targetSetter.getGenericParameterTypes()[0]), getGenericType(sourceGetter.getGenericReturnType()));
		final String loopMethodDescriptor = getMethodDescriptor(VOID_TYPE, getType(sourceGetter.getReturnType()), getType(targetSetter.getParameterTypes()[0]), getType(Function.class)); // public void transform(Iterable,Collection,Function)
		
/*
		List<Address> l = new ArrayList<>();
		target.setAddresses(l);//Lists.newArrayList(Iterables.transform(source.getPlaces().values(), t)));
		CollectionUtils.transForm(source.getAddresses(), l, t);

mv.visitTypeInsn(NEW, "org/spee/commons/convert/generator/GeneratorFactoryTest$2"); // new function
mv.visitInsn(DUP);
mv.visitVarInsn(ALOAD, 0);
mv.visitMethodInsn(INVOKESPECIAL, "org/spee/commons/convert/generator/GeneratorFactoryTest$2", "<init>", "(Lorg/spee/commons/convert/generator/GeneratorFactoryTest;)V", false);
mv.visitVarInsn(ASTORE, 5); // function
mv.visitTypeInsn(NEW, "java/util/ArrayList"); // new list
mv.visitInsn(DUP);
mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
mv.visitVarInsn(ASTORE, 6); // new list
mv.visitVarInsn(ALOAD, 2); // source
mv.visitVarInsn(ALOAD, 6); // new list 
mv.visitMethodInsn(INVOKEVIRTUAL, "org/spee/commons/beans/convert/DtoPerson", "setAddresses", "(Ljava/util/List;)V", false);
mv.visitVarInsn(ALOAD, 1); // target
mv.visitMethodInsn(INVOKEVIRTUAL, "org/spee/commons/beans/convert/Person", "getAddresses", "()Ljava/util/Collection;", false);
mv.visitVarInsn(ALOAD, 6); // new list
mv.visitVarInsn(ALOAD, 5); // function
mv.visitMethodInsn(INVOKESTATIC, "org/spee/commons/utils/CollectionUtils", "transformInto", "(Ljava/lang/Iterable;Ljava/util/Collection;Lcom/google/common/base/Function;)V", false);
 */
		final int LOCAL_VAR_LISTVALUE = 3;
		final int LOCAL_VAR_FUNCTION = 4;

		String functionClassname = "org/spee/commons/convert/generator/GeneratorFactoryTest$2";
		mv.visitTypeInsn(NEW, functionClassname); // new function
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, REFINSTANCE_THIS);
		mv.visitMethodInsn(INVOKESPECIAL, functionClassname, CONSTRUCTOR_INIT, "(Lorg/spee/commons/convert/generator/GeneratorFactoryTest;)V", false);
		mv.visitVarInsn(ASTORE, LOCAL_VAR_FUNCTION); // function
		
		// create new instance
		mv.visitInvokeDynamicInsn("newInstance", getMethodDescriptor(getType(targetSetter.getParameterTypes()[0])), newinstanceBootstrapMethod);
		mv.visitVarInsn(ASTORE, LOCAL_VAR_LISTVALUE);
		// save to setter
		mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_2);
		mv.visitVarInsn(ALOAD, LOCAL_VAR_LISTVALUE);
		mv.visitMethodInsn(INVOKEVIRTUAL, context.targetType.getDescriptor(), targetSetter.getName(), getMethodDescriptor(targetSetter), false);
		// loop and convert
		mv.visitVarInsn(ALOAD, REFINSTANCE_ARGUMENT_1);
		mv.visitMethodInsn(INVOKEVIRTUAL, context.sourceType.getDescriptor(), sourceGetter.getName(), getMethodDescriptor(sourceGetter), false);
		mv.visitVarInsn(ALOAD, LOCAL_VAR_LISTVALUE);
		mv.visitVarInsn(ALOAD, LOCAL_VAR_FUNCTION); // function
//		mv.visitInsn(ACONST_NULL); // getConverter()
////	mv.visitInvokeDynamicInsn("getConverter", convertMethodDescriptor, converterBootstrapMethod, "");
		mv.visitInvokeDynamicInsn("transform", loopMethodDescriptor, listBootstrapMethod); // CollectionUtils.transformInto(Iterable, Collection, Function);
	}

	
	
	/**
	 * Write bytecode for a single property conversion, where there should be used a specific converter.
	 * This converter is gotten from a local field.
	 * <pre>
	 * target.setValue( (TargetType)converter_0.convert(source.getValue()) );
	 * </pre>
	 * @param context
	 * @param mv
	 * @param fieldMap
	 * @param sourceGetter
	 * @param targetSetter
	 */
	private void generateLocalFieldconverterFieldMapping(final Context context, final MethodVisitor mv, final MappedProperties fieldMap) {
		final Method sourceGetter = fieldMap.sourceProperty.getReadMethod();
		final Method targetSetter = fieldMap.targetProperty.getWriteMethod();
		final ConverterField field = context.customConverters.get(fieldMap.customConverter);
		final Class<?> setterArgumentType = targetSetter.getParameterTypes()[0];

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
	}
}
