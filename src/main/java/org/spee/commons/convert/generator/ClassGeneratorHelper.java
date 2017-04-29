package org.spee.commons.convert.generator;

import static net.bytebuddy.jar.asm.Opcodes.ACONST_NULL;
import static net.bytebuddy.jar.asm.Opcodes.ARETURN;
import static net.bytebuddy.jar.asm.Opcodes.F_SAME;
import static net.bytebuddy.jar.asm.Opcodes.IFNONNULL;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;

import org.spee.commons.convert.generator.ClassMap.MappedProperties;

import com.google.common.base.Predicate;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

final class ClassGeneratorHelper {

	/**
	 * Add a check if the given variable is <code>null</code> and if so return <code>null</code>.
	 * @param mv The method to add the check to.
	 * @param variableIndex 
	 */
	public static class NullcheckAndReturnBuilder implements ByteCodeAppender, Implementation {
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

		@Override
		public Size apply(MethodVisitor methodVisitor, Context implementationContext, MethodDescription instrumentedMethod) {
			final Label jumpNullCheck = new Label();
			methodVisitor.visitVarInsn(Opcodes.ALOAD, variableIndex);
			methodVisitor.visitJumpInsn(IFNONNULL, jumpNullCheck);
			if( returnNull ){
				methodVisitor.visitInsn(ACONST_NULL);
				methodVisitor.visitInsn(ARETURN);
			} else {
				methodVisitor.visitInsn(RETURN);
			}
			methodVisitor.visitLabel(jumpNullCheck);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			return new Size(0, 0);
		}
		
	}


	
	public static Predicate<MappedProperties> filterOnlyCustomConverters(){
		return new Predicate<ClassMap.MappedProperties>() {
			@Override
			public boolean apply(MappedProperties input) {
				return input.customConverter != null;
			}
		};
	}

	
}
