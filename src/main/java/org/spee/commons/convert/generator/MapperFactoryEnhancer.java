package org.spee.commons.convert.generator;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.google.common.collect.Maps;

public class MapperFactoryEnhancer {

	
	public void handle() throws Exception {
		
		ClassReader reader = new ClassReader(getClass().getResourceAsStream("../MapperFactory.class"));
		ClassVisitor cv = new ClassVisitor(ASM5) {
			
			@Override
			public void visitEnd() {
				/**
				 * Add method
				 * public <S extends Object, T extends Object> T convert(S from, Class<T> toClass){
				 *   getConverter(from.getClass(), toClass).convert(from);
				 * }
				 */

				MethodVisitor mv = visitMethod(ACC_PUBLIC, "convert", null, null, null);
				
				mv.visitEnd();
				
				super.visitEnd();
			}
		};
		
	
		reader.accept(cv, 0);
	}
	
	
}
