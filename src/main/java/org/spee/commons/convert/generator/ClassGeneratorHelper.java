package org.spee.commons.convert.generator;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.IFNONNULL;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.spee.commons.convert.generator.ClassMap.MappedProperties;

import com.google.common.base.Predicate;

final class ClassGeneratorHelper {

	/**
	 * Add a check if the given variable is <code>null</code> and if so return <code>null</code>.
	 * @param mv The method to add the check to.
	 * @param variableIndex 
	 */
	public static void nullCheckWithReturn(MethodVisitor mv, int variableIndex) {
		/*
		 * if( source == null ) return null;
		 */
		final Label afterNullCheck = new Label();
		mv.visitVarInsn(ALOAD, variableIndex);
		mv.visitJumpInsn(IFNONNULL, afterNullCheck);
		mv.visitInsn(ACONST_NULL);
		mv.visitInsn(ARETURN);
		mv.visitLabel(afterNullCheck);
		mv.visitFrame(F_SAME, 0, null, 0, null);
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
