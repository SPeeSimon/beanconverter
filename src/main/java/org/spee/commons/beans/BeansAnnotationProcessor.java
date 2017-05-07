package org.spee.commons.beans;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractElementVisitor7;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.spee.commons.beans.Bean")
public class BeansAnnotationProcessor extends AbstractProcessor {

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		
		for (Element element : roundEnv.getElementsAnnotatedWith(Bean.class)) {
			
			String beanInfoName = element.getSimpleName() + "BeanInfo";
			Map<Object, TypeMirror> properties = new HashMap<>();
			ElementVisitor<Object, Map<Object, TypeMirror>> visitor = new AbstractElementVisitor7<Object, Map<Object, TypeMirror>>() {
				@Override
				public Object visitVariable(VariableElement e, Map<Object, TypeMirror> p) {
					p.put(e.getSimpleName(), e.asType());
					return null;
				}

				@Override
				public Object visitPackage(PackageElement e, Map<Object, TypeMirror> p) {
					return null;
				}

				@Override
				public Object visitType(TypeElement e, Map<Object, TypeMirror> p) {
					return null;
				}

				@Override
				public Object visitExecutable(ExecutableElement e, Map<Object, TypeMirror> p) {
					return null;
				}

				@Override
				public Object visitTypeParameter(TypeParameterElement e, Map<Object, TypeMirror> p) {
					return null;
				}
			};
			
			element.accept(visitor, properties);
			for (Element element2 : element.getEnclosedElements()) {
				element2.accept(visitor, properties);
			}
			
			try {
				writeClass(element, beanInfoName, properties);
			} catch (IOException e1) {
				processingEnv.getMessager().printMessage(Kind.ERROR, "Could not generate class " + beanInfoName, null);
			}
		}
		
		return false;
	}




	private void writeClass(Element element, String beanInfoName, Map<Object, TypeMirror> properties) throws IOException {
		JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(beanInfoName/*, element.getEnclosingElement()*/);

		try(PrintWriter writer = new PrintWriter(sourceFile.openWriter())){
			writer.printf("package %s;\n", processingEnv.getElementUtils().getPackageOf(element));
			writer.println();
			writer.println("import org.spee.commons.beans.Property;");
			writer.println("import org.spee.commons.beans.PropertyFactory;");
			writer.println("import com.google.common.reflect.TypeToken;");
			writer.println("import javax.annotation.Generated;");
			writer.println();
			writer.println("@Generated(\"org.spee.commons.beans.BeansAnnotationProcessor\")");
			writer.printf("public class %s implements org.spee.commons.beans.Type<%s> {\n\n", beanInfoName, element.asType());

			if( isGeneric(element.asType()) ){
				writer.printf("  private static final TypeToken<%s> CLASSTOKEN = %s;\n", element.asType(), getTypeTokenString(element.asType()));
			}else{
				writer.printf("  private static final Class<%1$s> CLASSTOKEN = %1$s.class;\n", element.asType());
			}
			writer.printf("  public String getName(){ return \"%s\"; }\n\n", element.getSimpleName());

			for (Entry<Object, TypeMirror> entry : properties.entrySet()) {
				final String propertyType = getTypeTokenOrClass(entry.getValue());
				writer.printf("  public static final Property<%3$s, %2$s> %1$s = PropertyFactory.getFor(\"%1$s\", %4$s, CLASSTOKEN);\n", entry.getKey(), entry.getValue(), element.asType(), propertyType);
			}

			writer.println("}");
		}
	}


	private String getTypeTokenOrClass(TypeMirror type){
		if( isGeneric(type) ){
			return String.format("new TypeToken<%s>(){}", type);
		}else{
			return String.format("%s.class", type);							
		}
	}
	
	/**
	 * If the type has generics, then return a new typetoken.
	 * @param type
	 * @return
	 */
	private String getTypeTokenString(TypeMirror type){
		if( isGeneric(type) ){
			return String.format("new TypeToken<%s>(){}", type);
		}else{
			return String.format("TypeToken.of(%s.class)", type);							
		}
	}
	
	private boolean isGeneric(TypeMirror type){
		return type.toString().contains("<");
	}
}
