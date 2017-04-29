package org.spee.commons.beans;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.Test;

public class TestAPT {
	
	private JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

	@Test
	public void test() throws Exception {
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		fileManager.setLocation(StandardLocation.SOURCE_PATH, Arrays.asList(new File("src/test/java")));
		fileManager.setLocation(StandardLocation.CLASS_OUTPUT,  Arrays.asList(new File("target/classes")));
		fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(new File("target/generated-sources")));

		Iterable<JavaFileObject> files = fileManager.list(StandardLocation.SOURCE_PATH, "", Collections.singleton(Kind.SOURCE), true);

		CompilationTask task = compiler.getTask(new PrintWriter(System.out), fileManager, null, null, null, files);
		task.setProcessors(Arrays.asList(new BeansAnnotationProcessor()));

		task.call();
	}

}
