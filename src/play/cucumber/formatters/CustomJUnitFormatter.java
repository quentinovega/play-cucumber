package play.cucumber.formatters;

import cucumber.runtime.CucumberException;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

//Copied from https://raw.github.com/cucumber/cucumber-jvm/master/core/src/main/java/cucumber/runtime/formatter/JUnitFormatter.java
public class CustomJUnitFormatter implements Formatter, Reporter {

	private final Writer out;
	private final Document doc;
	private final Element rootElement;

	private TestCase testCase;

	public CustomJUnitFormatter(File file) {
		try {
			out = new FileWriter(file);
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			rootElement = doc.createElement("testsuite");
			doc.appendChild(rootElement);
		} catch (IOException e1) {
			throw new CucumberException("Error while creating junit report", e1);
		} catch (ParserConfigurationException e2) {
			throw new CucumberException("Error while processing unit report", e2);
		}
	}

	private static String transformToPackageName(String tag) {
		return tag.toLowerCase().replace('-', '_').replaceAll("@", "");
	}

	private static String transformAnythingToClassName(String anything) {
		return Arrays.stream(anything.split(" "))
				.map(word -> word.split(","))
				.flatMap(Arrays::stream)
				.map(word -> word.split("'"))
				.flatMap(Arrays::stream)
				.map(word -> word.split("\""))
				.flatMap(Arrays::stream)
				.map(word -> word.split("\\."))
				.flatMap(Arrays::stream)
				.map(word -> word.split("\\-"))
				.flatMap(Arrays::stream)
				.map(word -> word.split("/"))
				.flatMap(Arrays::stream)
				.map(word -> word.split("_"))
				.flatMap(Arrays::stream)
				.map(word -> word.split(">"))
				.flatMap(Arrays::stream)
				.map(String::toLowerCase)
				.map(word -> word.replaceAll(" ", ""))
				.map(word -> word.replaceAll("\t", ""))
				.filter(word -> !word.isEmpty())
				.map(word -> Normalizer.normalize(word, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", ""))
				.map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
				.collect(Collectors.joining());
	}

	private static String transformAnythingToMethodName(String anything) {
		String className = transformAnythingToClassName(anything);
		return className.substring(0, 1).toLowerCase() + className.substring(1);
	}

	@Override
	public void feature(Feature feature) {
		if (feature.getTags() != null && !feature.getTags().isEmpty()) {
			String packageName = feature.getTags().stream().map(Tag::getName).map(CustomJUnitFormatter::transformToPackageName).collect(Collectors.joining("."));
			rootElement.setAttribute("package", packageName);
			rootElement.setAttribute("name", transformAnythingToClassName(feature.getName()));
		}
		TestCase.feature = feature;
	}

	@Override
	public void background(Background background) {
		testCase = new TestCase();
	}

	@Override
	public void scenario(Scenario scenario) {
		if (testCase != null) {
			testCase.scenario = scenario;
		} else {
			testCase = new TestCase(scenario);
		}

		increaseAttributeValue(rootElement, "tests");
	}

	@Override
	public void step(Step step) {
		if (testCase != null)
			testCase.steps.add(step);
	}

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {

    }

    @Override
	public void done() {
		try {
			// set up a transformer
			rootElement.setAttribute("failures", String.valueOf(rootElement.getElementsByTagName("failure").getLength()));
			TransformerFactory transfac = TransformerFactory.newInstance();
			Transformer trans = transfac.newTransformer();
			trans.setOutputProperty(OutputKeys.INDENT, "yes");
			StreamResult result = new StreamResult(out);
			DOMSource source = new DOMSource(doc);
			trans.transform(source, result);
		} catch (TransformerException e) {
			new CucumberException("Error while transforming.", e);
		}
	}

	@Override
	public void result(Result result) {
		testCase.results.add(result);

		if (testCase.scenario != null && testCase.results.size() == testCase.steps.size()) {
			rootElement.appendChild(testCase.writeTo(doc));
			testCase = null;
		}
	}

	@Override
	public void before(Match match, Result result) {
		handleHook(result);
	}

	@Override
	public void after(Match match, Result result) {
		handleHook(result);
	}

	private void handleHook(Result result) {
		if (result.getStatus().equals(Result.FAILED)) {
			if (testCase == null) {
				testCase = new TestCase();
			}
			testCase.results.add(result);
		}

	}

	private void increaseAttributeValue(Element element, String attribute) {
		int value = 0;
		if (element.hasAttribute(attribute)) {
			value = Integer.parseInt(element.getAttribute(attribute));
		}
		element.setAttribute(attribute, String.valueOf(++value));
	}

	@Override
	public void scenarioOutline(ScenarioOutline scenarioOutline) {
	}

	@Override
	public void examples(Examples examples) {
		TestCase.examples = examples.getRows().size() - 1;
	}

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {

    }

    @Override
	public void match(Match match) {
	}

	@Override
	public void embedding(String mimeType, byte[] data) {
	}

	@Override
	public void write(String text) {
	}

	@Override
	public void uri(String uri) {
	}

	@Override
	public void close() {
	}

	@Override
	public void eof() {
	}

	@Override
	public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
	}

	private static class TestCase {
		private static final DecimalFormat NUMBER_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);

		static {
			NUMBER_FORMAT.applyPattern("0.######");
		}

		private TestCase(Scenario scenario) {
			this.scenario = scenario;
		}

		private TestCase() {
		}

		Scenario scenario;
		static Feature feature;
		static int examples = 0;
		final List<Step> steps = new ArrayList<Step>();
		final List<Result> results = new ArrayList<Result>();

		private Element writeTo(Document doc) {
			Element tc = doc.createElement("testcase");
			tc.setAttribute("classname", transformAnythingToClassName(feature.getName()));
			String scenarioname = examples > 0 ? scenario.getName() + "_" + examples-- : scenario.getName();
			tc.setAttribute("name", transformAnythingToMethodName(scenarioname));
			long totalDurationNanos = 0;
			for (Result r : results) {
				totalDurationNanos += r.getDuration() == null ? 0 : r.getDuration();
			}

			double totalDurationSeconds = ((double) totalDurationNanos) / 1000000000;
			String time = NUMBER_FORMAT.format(totalDurationSeconds);
			tc.setAttribute("time", time);

			StringBuilder sb = new StringBuilder();
			Result skipped = null, failed = null;
			for (int i = 0; i < steps.size(); i++) {
				int length = sb.length();
				Result result = results.get(i);
				if ("failed".equals(result.getStatus()))
					failed = result;
				if ("undefined".equals(result.getStatus()) || "pending".equals(result.getStatus()))
					skipped = result;
				sb.append(steps.get(i).getKeyword());
				sb.append(steps.get(i).getName());
				for (int j = 0; sb.length() - length + j < 140; j++)
					sb.append(".");
				sb.append(result.getStatus());
				sb.append("\n");
			}
			Element child;
			if (failed != null) {
				sb.append("\nStackTrace:\n");
				StringWriter sw = new StringWriter();
				failed.getError().printStackTrace(new PrintWriter(sw));
				sb.append(sw.toString());
				child = doc.createElement("failure");
				child.setAttribute("message", failed.getErrorMessage());
				child.appendChild(doc.createCDATASection(sb.toString()));
			} else if (skipped != null) {
				child = doc.createElement("skipped");
				child.appendChild(doc.createCDATASection(sb.toString()));
			} else {
				child = doc.createElement("system-out");
				child.appendChild(doc.createCDATASection(sb.toString()));
			}
			tc.appendChild(child);
			return tc;
		}
	}

}
