package com.chsquill.bean.creator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.Modifier;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public class BeanCreator {

	private final static String INPUT_FILE = "./xml/";

	private final static String OUTPUT_DIR = "";

	private final static String PACKAGE_NAME = "com.test.error";

	private static Map<String, Integer> elementNames = new HashMap<>();

	public static void main(String[] args) {

		try {

			File file = new File(INPUT_FILE);

			DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

			Document doc = dBuilder.parse(file);

			if (doc.hasChildNodes()) {
				processNode(doc.getChildNodes());
			}

		} catch (Exception e) {

			e.printStackTrace();
		}

	}

	private static void processNode(NodeList nodeList) {

		for (int count = 0; count < nodeList.getLength(); count++) {

			Node tempNode = nodeList.item(count);

			// make sure it's element node
			if (tempNode.getNodeType() == Node.ELEMENT_NODE) {

				String activeNameName = tempNode.getNodeName();

				// hack to handle duplicate tag names
				if (elementNames.containsKey(activeNameName)) {
					Integer n = elementNames.get(activeNameName);
					n += 1;
					elementNames.put(activeNameName, n);
					activeNameName = activeNameName + n;
				} else {
					elementNames.put(activeNameName, 0);
				}

				TypeSpec.Builder typeSpec = TypeSpec.classBuilder(activeNameName).addModifiers(Modifier.PUBLIC);

				if (tempNode.hasChildNodes()) {

					NodeList childList = tempNode.getChildNodes();

					for (int i = 0; i < childList.getLength(); i++) {

						Node childNode = childList.item(i);

						// hack to get around issue of element text type
						if (childNode.getNodeName().contains("#"))
							continue;

						if (childNode.hasChildNodes()) {
							writeElement(typeSpec, childNode);
						} else {
							writeStringElement(typeSpec, childNode);
						}
					}

					writeAttribute(typeSpec, tempNode);

					createJavaFile(typeSpec);

					processNode(tempNode.getChildNodes());

				} else {

					writeAttribute(typeSpec, tempNode);

					writeStringElement(typeSpec, tempNode);
				}
			}
		}
	}

	private static void createJavaFile(TypeSpec.Builder typeSpec) {

		try {

			File sourcePath = new File(OUTPUT_DIR);

			if (!sourcePath.exists()) {
				sourcePath.mkdirs();
			}

			JavaFile javaFile = JavaFile.builder(PACKAGE_NAME, typeSpec.build()).build();

			javaFile.writeTo(sourcePath);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeElement(TypeSpec.Builder typeSpec, Node tempNode) {

		String activeNameName = tempNode.getNodeName();

		// hack to handle duplicate tag names
		if (elementNames.containsKey(activeNameName)) {
			Integer n = elementNames.get(activeNameName);
			activeNameName = activeNameName + (n + 1);
		}

		String typeName = toPascalCase(activeNameName);
		String fieldName = toCamelCase(activeNameName);

		typeSpec.addField(FieldSpec.builder(ClassName.get(PACKAGE_NAME, typeName), fieldName, Modifier.PRIVATE)
				.addAnnotation(AnnotationSpec.builder(JacksonXmlProperty.class)
						.addMember("localName", ("\"" + tempNode.getNodeName() + "\"")).build())
				.build());

		writeGettersSetters(typeSpec, fieldName);
	}

	private static void writeStringElement(TypeSpec.Builder typeSpec, Node tempNode) {

		String activeNameName = tempNode.getNodeName();

		String typeName = toPascalCase(activeNameName);
		String fieldName = toCamelCase(activeNameName);

		typeSpec.addField(FieldSpec.builder("".getClass(), fieldName, Modifier.PRIVATE)
				.addAnnotation(AnnotationSpec.builder(JacksonXmlProperty.class)
						.addMember("localName", ("\"" + tempNode.getNodeName() + "\"")).build())
				.build());

		writeStringGettersSetters(typeSpec, fieldName);
	}

	private static void writeAttribute(TypeSpec.Builder typeSpec, Node tempNode) {

		if (tempNode.hasAttributes()) {

			// get attributes names and values
			NamedNodeMap nodeMap = tempNode.getAttributes();

			for (int j = 0; j < nodeMap.getLength(); j++) {
				Node node = nodeMap.item(j);
				String fieldName = toCamelCase(node.getNodeName());
				typeSpec.addField(FieldSpec.builder("".getClass(), fieldName, Modifier.PRIVATE)
						.addAnnotation(AnnotationSpec.builder(JacksonXmlProperty.class).addMember("isAttribute", "true")
								.addMember("localName", ("\"" + node.getNodeName() + "\"")).build())
						.build());
			}
		}
	}

	private static void writeGettersSetters(TypeSpec.Builder typeSpec, String field) {

		String typeName = toPascalCase(field);
		String fieldName = toCamelCase(field);

		MethodSpec getter = MethodSpec.methodBuilder("get" + typeName).addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("return this.%s", fieldName)).returns(ClassName.get(PACKAGE_NAME, typeName))
				.build();
		typeSpec.addMethod(getter);

		MethodSpec setter = MethodSpec.methodBuilder("set" + typeName).addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("this.%s=%s", fieldName, fieldName))
				.addParameter(ClassName.get(PACKAGE_NAME, typeName), fieldName).build();
		typeSpec.addMethod(setter);
	}

	private static void writeStringGettersSetters(TypeSpec.Builder typeSpec, String field) {

		String typeName = toPascalCase(field);
		String fieldName = toCamelCase(field);

		MethodSpec getter = MethodSpec.methodBuilder("get" + typeName).addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("return this.%s", fieldName)).returns("".getClass()).build();
		typeSpec.addMethod(getter);

		MethodSpec setter = MethodSpec.methodBuilder("set" + typeName).addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("this.%s=%s", fieldName, fieldName)).addParameter("".getClass(), fieldName)
				.build();
		typeSpec.addMethod(setter);
	}

	private static String toPascalCase(String value) {
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String toCamelCase(String value) {
		return Character.toLowerCase(value.charAt(0)) + value.substring(1);
	}

}
