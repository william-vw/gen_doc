package gen_doc;

import static org.apache.jen3.n3.N3ModelSpec.Types.N3_MEM;
import static org.apache.jen3.n3.N3ModelSpec.Types.N3_MEM_FP_INF;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.jen3.n3.FeedbackActions;
import org.apache.jen3.n3.N3Feedback;
import org.apache.jen3.n3.N3MistakeTypes;
import org.apache.jen3.n3.N3Model;
import org.apache.jen3.n3.N3ModelSpec;
import org.apache.jen3.rdf.model.ModelFactory;
import org.apache.jen3.rdf.model.Resource;
import org.apache.jen3.vocabulary.RDF;
import org.apache.jen3.vocabulary.RDFS;

import wvw.utils.rdf.NS;

public class GenBuiltinDoc {

	private String builtinsPath = "builtins.n3";
	private String nsFolder = "ns/";
	private String genSchemaPath = "rules/gen_schemas.n3";
	private String genEntriesPath = "rules/gen_entries.n3";
	private String genHtmlPath = "rules/gen_html.n3";
	private String outFolder = "out/";

	private String htmlName = "builtins.html";

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(Option.builder("builtins").argName("builtins").hasArg().desc("builtins.n3 path")
				.required(false).build());
		options.addOption(Option.builder("ns").argName("ns").hasArg()
				.desc("folder for NS builtin files (e.g., math.n3)").required(false).build());
		options.addOption(
				Option.builder("schema").argName("schema").hasArg().desc("gen_schema.n3 path").required(false).build());
		options.addOption(Option.builder("entries").argName("entries").hasArg().desc("gen_entries.n3 path")
				.required(false).build());
		options.addOption(
				Option.builder("html").argName("html").hasArg().desc("gen_html.n3 path").required(false).build());
		options.addOption(Option.builder("out").argName("out").hasArg().desc("output folder").required(false).build());

		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println("ERROR: " + exp.getMessage());
			System.exit(1);
		}

		GenBuiltinDoc gen = new GenBuiltinDoc();

		if (line.hasOption("builtins"))
			gen.builtinsPath = line.getOptionValue("builtins");

		if (line.hasOption("ns"))
			gen.nsFolder = line.getOptionValue("ns");

		if (line.hasOption("schema"))
			gen.genSchemaPath = line.getOptionValue("schema");

		if (line.hasOption("entries"))
			gen.genEntriesPath = line.getOptionValue("entries");

		if (line.hasOption("html"))
			gen.genEntriesPath = line.getOptionValue("html");

		if (line.hasOption("out"))
			gen.outFolder = line.getOptionValue("out");

		gen.generate();

	}

	public void generate() throws IOException {
		// no need for schema-gen if builtins.n3 was not updated since last gen
		File htmlFile = new File(outFolder, htmlName);
		if (htmlFile.exists()) {

			if (new File(builtinsPath).lastModified() > htmlFile.lastModified()) {
				System.out.println("builtins.n3 updated - regenerating builtin schemas\n");

				prepBuiltins();
				genSchemas();
			}
		}

		genEntries();
		genHtml();
	}

	// TODO in builtins n3 file, auto-generate 'accept' instead of listing them
	// (essentially this involves adding variables to all atomic constraints)
	// instead of this mess (not really needed, just speed things up)

	public void prepBuiltins() throws IOException {
		long start = System.currentTimeMillis();

		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));

		N3Model m = ModelFactory.createN3Model(spec);
		m.read(new FileInputStream(builtinsPath), "");

		List<String> properties = Arrays.asList(NS.toUri("builtin:subject"), NS.toUri("builtin:object"),
				NS.toUri("builtin:restricts"));
		m.listStatements(null, m.createResource(NS.toUri("builtin:accept")), (Resource) null).toList().forEach(stmt -> {
			removeRecursively(stmt.getObject(), properties);
			stmt.remove();
		});

//		m.write(System.out);
		m.write(new FileOutputStream(outFolder + "builtins_domains.n3"));

		long end = System.currentTimeMillis();
		System.out.println("prepped builtins (time: " + (end - start) + "ms)\n");
	}

	private void removeRecursively(Resource s, List<String> properties) {
		s.listProperties().toList().forEach(stmt -> {
			if (properties.contains(stmt.getPredicate().getURI())) {

				removeRecursively(stmt.getObject(), properties);
				stmt.remove();
			}
		});
	}

	public void genSchemas() throws IOException {

		System.out.println("generating schemas...");

		long start = System.currentTimeMillis();

		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));

		N3Model m = ModelFactory.createN3Model(spec);
		m.read(new FileInputStream(outFolder + "builtins_domains.n3"), "");
		m.read(new FileInputStream(genSchemaPath), "");

//		m.write(System.out);

		N3Model out = ModelFactory.createN3Model(N3ModelSpec.get(N3_MEM));

		Set<String> allBuiltins = m
				.listStatements(null, RDF.type, m.createResource(NS.toUri("builtin:BuiltinDefinition"))).toList()
				.stream().map(s -> s.getSubject().getURI()).collect(Collectors.toSet());

		Set<String> schemaBuiltins = new HashSet<>();

		m.listStatements(null, m.createResource(NS.toUri("builtin:schema")), (Resource) null).forEachRemaining(stmt -> {
			schemaBuiltins.add(stmt.getSubject().getURI());

//			String label = s.getSubject().getPropertyResourceValue(RDFS.label).asLiteral().getString();
//			System.out.println(s.getSubject().getURI() + ":\n" + label);

			out.add(stmt);
		});

		long end = System.currentTimeMillis();
		System.out.println("schemas for " + schemaBuiltins.size() + " out of " + allBuiltins.size() + " builtins");

		allBuiltins.removeAll(schemaBuiltins);
		System.out.println("no schema: " + allBuiltins);

		out.write(new FileOutputStream(outFolder + "schemas.n3"));

		System.out.println("generated schemas (time: " + (end - start) + "ms)\n");
	}

	public void genEntries() throws IOException {
		long start = System.currentTimeMillis();

		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));

		N3Model m = ModelFactory.createN3Model(spec);
		m.read(new FileInputStream(outFolder + "schemas.n3"), "");

		// insert comments

		N3Model ns = ModelFactory.createN3Model(spec);
		ns.read(new FileInputStream(nsFolder + "crypto.n3"), "");
		ns.read(new FileInputStream(nsFolder + "list.n3"), "");
		ns.read(new FileInputStream(nsFolder + "log.n3"), "");
		ns.read(new FileInputStream(nsFolder + "math.n3"), "");
		ns.read(new FileInputStream(nsFolder + "string.n3"), "");
		ns.read(new FileInputStream(nsFolder + "time.n3"), "");

		m.listStatements(null, m.createResource(NS.toUri("builtin:schema")), (Resource) null).toList().forEach(stmt -> {
			Resource builtin = stmt.getSubject();

			if (ns.contains(builtin, RDFS.comment))
				m.add(ns.listStatements(builtin, RDFS.comment, (Resource) null).next());
		});

		// generate entries

		m.read(new FileInputStream(genEntriesPath), "");

//		m.getDeductionsModel().write(System.out);

		String outPath = outFolder + "entries.n3";
		m.getDeductionsModel().write(new FileOutputStream(outPath));

		long end = System.currentTimeMillis();
		System.out.println("generated entries (time: " + (end - start) + "ms)\n");
	}

	public void genHtml() throws IOException {
		long start = System.currentTimeMillis();

		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));

		N3Model m = ModelFactory.createN3Model(spec);
		m.read(new FileInputStream(outFolder + "entries.n3"), "");

		// generate entries

		m.read(new FileInputStream(genHtmlPath), "");

		m.getDeductionsModel().write(System.out);

		String outPath = outFolder + htmlName;
		String html = m.listStatements(null, m.createResource(NS.toUri("builtin:out")), (Resource) null).next()
				.getObject().asLiteral().getLexicalForm();

		Set<Resource> builtins = new HashSet<>(); 
		m.listStatements(null, m.createResource(NS.toUri("builtin:builtinHtml")), (Resource) null)
				.forEachRemaining(stmt -> {
					
					if (builtins.contains(stmt.getSubject()))
						System.out.println("DING DONG " + stmt.getSubject().getURI());
					builtins.add(stmt.getSubject());
				});

		Files.writeString(Paths.get(outPath), html);

		long end = System.currentTimeMillis();
		System.out.println("generated html (time: " + (end - start) + "ms)\n");
	}

//	public void genHtml() throws IOException {
//		long start = System.currentTimeMillis();
//
//		StringBuffer html = new StringBuffer();
//		html.append("<html><head><link href='style.css' rel='stylesheet' /></head><body>");
//
//		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
//		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));
//
//		N3Model m = ModelFactory.createN3Model(spec);
//		m.read(new FileInputStream(outFolder + "entries.n3"), "");
//
//		m.listStatements(null, m.createResource(NS.toUri("builtin:entry")), (Resource) null).forEachRemaining(stmt -> {
//			Resource entry = stmt.getObject();
//			String prefix = entry.getPropertyResourceValue(m.createResource(NS.toUri("builtin:prefix"))).asLiteral()
//					.getLexicalForm();
//			String ns = entry.getPropertyResourceValue(m.createResource(NS.toUri("builtin:namespace"))).asLiteral()
//					.getLexicalForm();
//
////			System.out.println("-- " + prefix + " (" + ns + ")\n");
//
//			html.append("<div>");
//			html.append("<h2>").append(prefix).append("</h2>");
//			html.append("(").append(ns).append(")");
//
//			List<Statement> stmts2 = m
//					.listStatements(entry, m.createResource(NS.toUri("builtin:builtin")), (Resource) null).toList();
//			// sort builtins alphabetically on name
//			stmts2.sort((s1, s2) -> {
//				return s1.getObject().getPropertyResourceValue(m.createResource(NS.toUri("builtin:name"))).asLiteral()
//						.getLexicalForm()
//						.compareTo(s2.getObject().getPropertyResourceValue(m.createResource(NS.toUri("builtin:name")))
//								.asLiteral().getLexicalForm());
//			});
//
//			stmts2.forEach(stmt2 -> {
//				Resource builtin = stmt2.getObject();
//
//				String uri = builtin.getPropertyResourceValue(m.createResource(NS.toUri("builtin:uri"))).getURI();
//				String name = builtin.getPropertyResourceValue(m.createResource(NS.toUri("builtin:name"))).asLiteral()
//						.getLexicalForm();
//				String schema = builtin.getPropertyResourceValue(m.createResource(NS.toUri("builtin:schema")))
//						.asLiteral().getLexicalForm();
//				String comment = null;
//				if (builtin.hasProperty(RDFS.comment)) {
//					comment = builtin.getPropertyResourceValue(RDFS.comment).asLiteral().getLexicalForm();
//					comment = comment.replaceAll("\n\n", "<br />");
//				}
//
////				System.out.println(name + " (" + uri + ")\n" + descr + "\n");
//
//				schema = schema.replace("\\n", "<br />").replace("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
//				html.append("<div>");
//				html.append("<h4>").append(name).append("</h4>");
//				html.append("(").append(uri).append(")");
//				html.append("<h5>schema</h5>");
//				html.append("<p>").append(schema).append("</p>");
//				if (comment != null) {
//					html.append("<h5>comment</h5>");
//					html.append("<p>").append(comment).append("</p>");
//				}
//				html.append("</div>");
//			});
//			html.append("</div>");
//
////			System.out.println("\n");
//		});
//
//		html.append("</body></html>");
//
//		String outPath = outFolder + htmlName;
//		IOUtils.writeFile(outPath, html.toString(), false);
//
//		long end = System.currentTimeMillis();
//		System.out.println("generated html (time: " + (end - start) + "ms)\n");
//	}
}
