package gen_doc;

import static org.apache.jen3.n3.N3ModelSpec.Types.N3_MEM;
import static org.apache.jen3.n3.N3ModelSpec.Types.N3_MEM_FP_INF;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jen3.n3.FeedbackActions;
import org.apache.jen3.n3.N3Feedback;
import org.apache.jen3.n3.N3MistakeTypes;
import org.apache.jen3.n3.N3Model;
import org.apache.jen3.n3.N3ModelSpec;
import org.apache.jen3.rdf.model.ModelFactory;
import org.apache.jen3.rdf.model.Resource;
import org.apache.jen3.rdf.model.Statement;
import org.apache.jen3.util.IOUtils;
import org.apache.jen3.vocabulary.RDF;
import org.apache.jen3.vocabulary.RDFS;

import wvw.utils.rdf.NS;

public class GenBuiltinDoc {

	private static String builtinsPath = "src/main/resources/builtins_illiberal.n3";

	public static void main(String[] args) throws Exception {
//		prepBuiltins();

		genSchemas();
		genEntries();
		genHtml();
	}

	// TODO in builtins n3 file, auto-generate 'accept' instead of listing them
	// (essentially this involves adding variables to all atomic constraints)
	// instead of this mess (not really needed, just speed things up)
	public static void prepBuiltins() throws IOException {
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

		String outPath = "src/main/resources/out/builtins_domains.n3";
//		m.write(System.out);
		m.write(new FileOutputStream(outPath));

		long end = System.currentTimeMillis();
		System.out.println("prepped builtins (time: " + (end - start) + "ms)");
	}

	private static void removeRecursively(Resource s, List<String> properties) {
		s.listProperties().toList().forEach(stmt -> {
			if (properties.contains(stmt.getPredicate().getURI())) {

				removeRecursively(stmt.getObject(), properties);
				stmt.remove();
			}
		});
	}

	public static void genSchemas() throws IOException {
		long start = System.currentTimeMillis();

		String builtinsPath = "src/main/resources/out/builtins_domains.n3";
		String rulesPath = "src/main/resources/rules/gen_schemas.n3";

		String outPath = "src/main/resources/out/schemas.n3";

		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));

		N3Model m = ModelFactory.createN3Model(spec);
		m.read(new FileInputStream(builtinsPath), "");
		m.read(new FileInputStream(rulesPath), "");

//		m.getDeductionsModel().write(System.out);

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
		System.out.println("\nschemas for " + schemaBuiltins.size() + " out of " + allBuiltins.size() + " builtins");

		allBuiltins.removeAll(schemaBuiltins);
		System.out.println("no schema: " + allBuiltins);

		out.write(new FileOutputStream(outPath));

		System.out.println("generated schemas (time: " + (end - start) + "ms)");
	}

	public static void genEntries() throws IOException {
		long start = System.currentTimeMillis();

		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));

		N3Model m = ModelFactory.createN3Model(spec);
		m.read(new FileInputStream("src/main/resources/out/schemas.n3"), "");

		// insert comments

		N3Model ns = ModelFactory.createN3Model(spec);
		ns.read(new FileInputStream("src/main/resources/ns/crypto.n3"), "");
		ns.read(new FileInputStream("src/main/resources/ns/list.n3"), "");
		ns.read(new FileInputStream("src/main/resources/ns/log.n3"), "");
		ns.read(new FileInputStream("src/main/resources/ns/math.n3"), "");
		ns.read(new FileInputStream("src/main/resources/ns/string.n3"), "");
		ns.read(new FileInputStream("src/main/resources/ns/time.n3"), "");

		m.listStatements(null, m.createResource(NS.toUri("builtin:schema")), (Resource) null).toList().forEach(stmt -> {
			Resource builtin = stmt.getSubject();

			if (ns.contains(builtin, RDFS.comment))
				m.add(ns.listStatements(builtin, RDFS.comment, (Resource) null).next());
		});

		// generate entries

		m.read(new FileInputStream("src/main/resources/rules/gen_entries.n3"), "");

//		m.getDeductionsModel().write(System.out);

		String outPath = "src/main/resources/out/entries.n3";
		m.getDeductionsModel().write(new FileOutputStream(outPath));

		long end = System.currentTimeMillis();
		System.out.println("\ngenerated entries (time: " + (end - start) + "ms)");
	}

	public static void genHtml() throws IOException {
		long start = System.currentTimeMillis();

		StringBuffer html = new StringBuffer();
		html.append("<html><head><link href='style.css' rel='stylesheet' /></head><body>");

		N3ModelSpec spec = N3ModelSpec.get(N3_MEM_FP_INF);
		spec.setFeedback(new N3Feedback(N3MistakeTypes.BUILTIN_MISUSE_NS, FeedbackActions.NONE));

		N3Model m = ModelFactory.createN3Model(spec);
		m.read(new FileInputStream("src/main/resources/out/entries.n3"), "");

		m.listStatements(null, m.createResource(NS.toUri("builtin:entry")), (Resource) null).forEachRemaining(stmt -> {
			Resource entry = stmt.getObject();
			String prefix = entry.getPropertyResourceValue(m.createResource(NS.toUri("builtin:prefix"))).asLiteral()
					.getLexicalForm();
			String ns = entry.getPropertyResourceValue(m.createResource(NS.toUri("builtin:namespace"))).asLiteral()
					.getLexicalForm();

//			System.out.println("-- " + prefix + " (" + ns + ")\n");

			html.append("<div>");
			html.append("<h2>").append(prefix).append("</h2>");
			html.append("(").append(ns).append(")");

			List<Statement> stmts2 = m
					.listStatements(entry, m.createResource(NS.toUri("builtin:builtin")), (Resource) null).toList();
			// sort builtins alphabetically on name
			stmts2.sort((s1, s2) -> {
				return s1.getObject().getPropertyResourceValue(m.createResource(NS.toUri("builtin:name"))).asLiteral()
						.getLexicalForm()
						.compareTo(s2.getObject().getPropertyResourceValue(m.createResource(NS.toUri("builtin:name")))
								.asLiteral().getLexicalForm());
			});

			stmts2.forEach(stmt2 -> {
				Resource builtin = stmt2.getObject();

				String uri = builtin.getPropertyResourceValue(m.createResource(NS.toUri("builtin:uri"))).getURI();
				String name = builtin.getPropertyResourceValue(m.createResource(NS.toUri("builtin:name"))).asLiteral()
						.getLexicalForm();
				String schema = builtin.getPropertyResourceValue(m.createResource(NS.toUri("builtin:schema")))
						.asLiteral().getLexicalForm();
				String comment = null;
				if (builtin.hasProperty(RDFS.comment)) {
					comment = builtin.getPropertyResourceValue(RDFS.comment).asLiteral().getLexicalForm();
					comment = comment.replaceAll("\n\n", "<br />");
				}

//				System.out.println(name + " (" + uri + ")\n" + descr + "\n");

				schema = schema.replace("\\n", "<br />").replace("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
				html.append("<div>");
				html.append("<h4>").append(name).append("</h4>");
				html.append("(").append(uri).append(")");
				html.append("<h5>schema</h5>");
				html.append("<p>").append(schema).append("</p>");
				if (comment != null) {
					html.append("<h5>comment</h5>");
					html.append("<p>").append(comment).append("</p>");
				}
				html.append("</div>");
			});
			html.append("</div>");

//			System.out.println("\n");
		});

		html.append("</body></html>");

		String outPath = "src/main/resources/out/builtins.html";
		IOUtils.writeFile(outPath, html.toString(), false);

		long end = System.currentTimeMillis();
		System.out.println("\ngenerated spec (time: " + (end - start) + "ms)");
	}
}
