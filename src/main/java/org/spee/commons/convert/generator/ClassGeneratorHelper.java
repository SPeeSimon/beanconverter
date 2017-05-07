package org.spee.commons.convert.generator;

import static net.bytebuddy.jar.asm.Opcodes.ACONST_NULL;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ARETURN;
import static net.bytebuddy.jar.asm.Opcodes.F_SAME;
import static net.bytebuddy.jar.asm.Opcodes.IFNONNULL;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;

import java.util.Base64;
import java.util.Base64.Encoder;

import org.spee.commons.convert.generator.ClassMap.MappedProperties;

import com.google.common.base.Predicate;

import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.Implementation.Composable;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.utility.RandomString;

final class ClassGeneratorHelper {

	/**
	 * Add a check if the given variable is <code>null</code> and if so return <code>null</code>.
	 * @param mv The method to add the check to.
	 * @param variableIndex 
	 */
	public static class NullcheckAndReturnBuilder implements ByteCodeAppender, Composable {
		private int variableIndex;
		private boolean returnNull;

		/**
		 * defaults: 1, true (first argument, returning null)
		 * @see NullcheckAndReturnBuilder#NullcheckAndReturnBuilder(int, boolean)
		 */
		public NullcheckAndReturnBuilder() {
			this(1, true);
		}
		
		/**
		 * defaults: true (returning null)
		 * @see NullcheckAndReturnBuilder#NullcheckAndReturnBuilder(int, boolean)
		 */
		public NullcheckAndReturnBuilder(int offset) {
			this(offset, true);
		}
		
		/**
		 * defaults: 1 (first argument)
		 * @see NullcheckAndReturnBuilder#NullcheckAndReturnBuilder(int, boolean)
		 */
		public NullcheckAndReturnBuilder(boolean returnNull) {
			this(1, returnNull);
		}
		
		/**
		 * Check the value at the offset for null. If so, then return.
		 * @param offset The offset to check for null. Given the method <code>public Object check(Object val1){}</code> 
		 * offset 1 = this, 2 = val1.
		 * @param returnNull true to return a null value, false for void.
		 */
		public NullcheckAndReturnBuilder(int offset, boolean returnNull) {
			this.variableIndex = offset;
			this.returnNull = returnNull;
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
		 * Return a new {@link Implementation} that is a compound of this and the given implementation.
		 */
		@Override
		public Implementation andThen(Implementation implementation) {
			return new Implementation.Compound(this, implementation);
		}
		
		/**
		 * if ( offset == null ){ return; }
		 * if ( offset == null ){ return null; }
		 */
		@Override
		public Size apply(MethodVisitor methodVisitor, Context implementationContext, MethodDescription instrumentedMethod) {
			final Label jumpSkipReturn = new Label();
			methodVisitor.visitVarInsn(ALOAD, variableIndex);
			methodVisitor.visitJumpInsn(IFNONNULL, jumpSkipReturn);
			if( returnNull ){
				methodVisitor.visitInsn(ACONST_NULL);
				methodVisitor.visitInsn(ARETURN);
			} else {
				methodVisitor.visitInsn(RETURN);
			}
			methodVisitor.visitLabel(jumpSkipReturn);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			return new Size(0, 0);
		}
		
	}


	/**
	 * Name the type, with a fixed package and classname that is a Base64 value of the given types.
	 * The Base64 is generated from: [source.class] to [target.class]
	 * <br>
	 * {@value GeneratorFactory#DEFAULT_PACKAGE}.gen_[SourceType to TargetType]
	 * @author shave
	 */
	static class TypeNamingStrategy extends NamingStrategy.AbstractBase {
		public static final TypeNamingStrategy INSTANCE = new TypeNamingStrategy();
		private Encoder encoder = Base64.getEncoder();
		
		private TypeNamingStrategy(){}
		
		@Override
		public String subclass(Generic superClass) {
			String src = superClass.getTypeArguments().get(0).getTypeName() + " to " + superClass.getTypeArguments().get(1).getTypeName();
			return GeneratorFactory.DEFAULT_PACKAGE + ".gen_" + new String(encoder.encode(src.getBytes())).replace("=", "");
		}
		
		@Override
		protected String name(TypeDescription superClass) {
			return GeneratorFactory.DEFAULT_PACKAGE + ".gen_SourceType_to_TargetTypeConverter" + RandomString.make(10);
		}
	}


	/**
	 * {@link Predicate} that return <code>true</code> when the {@link MappedProperties} has a custom converter set.
	 * @return
	 * @see MappedProperties#hasCustomConverter()
	 */
	public static Predicate<MappedProperties> filterOnlyCustomConverters(){
		return new Predicate<ClassMap.MappedProperties>() {
			@Override
			public boolean apply(MappedProperties input) {
				return input.hasCustomConverter();
			}
		};
	}


	/**
	 * If the given type is a primitive, then return the wrapper class.
	 * @param type Some type
	 * @return If the type represents a boolean, byte, short, char, int, long, float or double
	 * then return a {@link TypeDescription} for the java.util wrapper class.
	 * If the given type does not represent a primitive, then return null;
	 */
	public static TypeDescription getPrimitiveWrapperType(TypeDefinition type) {
		if( type.isPrimitive() ){
	        if (type.represents(boolean.class)) {
	            return new TypeDescription.ForLoadedType(Boolean.class);
	        } else if (type.represents(byte.class)) {
	            return new TypeDescription.ForLoadedType(Byte.class);
	        } else if (type.represents(short.class)) {
	            return new TypeDescription.ForLoadedType(Short.class);
	        } else if (type.represents(char.class)) {
	            return new TypeDescription.ForLoadedType(Character.class);
	        } else if (type.represents(int.class)) {
	            return new TypeDescription.ForLoadedType(Integer.class);
	        } else if (type.represents(long.class)) {
	            return new TypeDescription.ForLoadedType(Long.class);
	        } else if (type.represents(float.class)) {
	            return new TypeDescription.ForLoadedType(Float.class);
	        } else if (type.represents(double.class)) {
	            return new TypeDescription.ForLoadedType(Double.class);
	        }
		}
		return null;
	}
	
}
