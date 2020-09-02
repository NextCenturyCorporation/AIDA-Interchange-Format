package com.ncc.aif;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.LoggerFactory;
import org.topbraid.jenax.util.ARQFactory;
import org.topbraid.shacl.arq.SHACLFunctions;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.engine.Shape;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.js.SHACLScriptEngineManager;
import org.topbraid.shacl.util.SHACLUtil;
import org.topbraid.shacl.validation.ClassesCache;
import org.topbraid.shacl.validation.MaximumNumberViolations;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationUtil;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

public class InstrumentedValidationEngine extends ValidationEngine {
    private Predicate<RDFNode> focusNodeFilter;

    protected InstrumentedValidationEngine(Dataset dataset, URI shapesGraphURI, ShapesGraph shapesGraph, Resource report) {
        super(dataset, shapesGraphURI, shapesGraph, report);
    }

    @Override
    public Resource validateAll() throws InterruptedException {
		final Logger logger = getLogger("validation_times.tsv");

		boolean nested = SHACLScriptEngineManager.begin();
		try {
            List<Shape> rootShapes = shapesGraph.getRootShapes();
            
            if(monitor != null) {
				monitor.beginTask("Validating " + rootShapes.size() + " shapes", rootShapes.size());
			}
			if(getClassesCache() == null) {
				// If we are doing everything then the cache should be used, but not for individual nodes
				setClassesCache(new ClassesCache());
			}
			int i = 0;
			int debugCount = 0;
			for(Shape shape : rootShapes) {

				if(monitor != null) {
					monitor.subTask("Shape " + (++i) + ": " + getLabelFunction().apply(shape.getShapeResource()));
				}
				
				long start = System.currentTimeMillis();
				Collection<RDFNode> focusNodes = shape.getTargetNodes(dataset);
				logger.debug(String.format("%s\tCollecting %d nodes\t%d", getShapeString(shape, ++debugCount), 
					focusNodes.size(), System.currentTimeMillis() - start));
				
				if(focusNodeFilter != null) {
					List<RDFNode> filteredFocusNodes = new LinkedList<RDFNode>();
					for(RDFNode focusNode : focusNodes) {
						if(focusNodeFilter.test(focusNode)) {
							filteredFocusNodes.add(focusNode);
						}
					}
					focusNodes = filteredFocusNodes;
				}
				if(!focusNodes.isEmpty()) {
					for(Constraint constraint : shape.getConstraints()) {
						long cStart = System.currentTimeMillis();
						validateNodesAgainstConstraint(focusNodes, constraint);
						logger.debug(String.format("%s\t%s(%s)\t%d",
							getShapeString(shape, debugCount),
							constraint.getComponent().getLocalName(),
							getParameterString(constraint.getParameterValue()),
							System.currentTimeMillis() - cStart));
					}
					logger.debug(String.format("%s\ttotal\t%d", getShapeString(shape, debugCount), 
						System.currentTimeMillis() - start));
				}
				if(monitor != null) {
					monitor.worked(1);
					if(monitor.isCanceled()) {
						throw new InterruptedException();
					}
				}
			}
		}
		catch(MaximumNumberViolations ex) {
			// ignore
		}
		finally {
			SHACLScriptEngineManager.end(nested);
		}
		updateConforms();
		return getReport();
	}

	private static String getParameterString(RDFNode parameter) {
		return parameter != null && parameter.isResource() ? parameter.asResource().getLocalName() :
			String.valueOf(parameter);
	}

	private static String getShapeString(Shape shape, int count) {
		return String.format("%d-%s", count, shape.toString());
	}

	private static Logger logger;
	private Logger getLogger(String filename) {
		if (logger == null) {
			LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
			PatternLayoutEncoder ple = new PatternLayoutEncoder();

			ple.setPattern("%msg%n");
			ple.setContext(lc);
			ple.start();
			FileAppender<ILoggingEvent> fileAppender = new FileAppender<ILoggingEvent>();
			fileAppender.setFile(filename);
			fileAppender.setEncoder(ple);
			fileAppender.setContext(lc);
			fileAppender.start();

			logger = (Logger) LoggerFactory.getLogger("InstrumentedValidationEngine");
			logger.addAppender(fileAppender);
			logger.setLevel(Level.DEBUG);
			logger.setAdditive(false);
		}
		
		return logger;
	}

    @Override
    public void setFocusNodeFilter(Predicate<RDFNode> value) {
        focusNodeFilter = value;
        super.setFocusNodeFilter(value);
    }

	/**
	 * Copy of {@link org.topbraid.shacl.validation.ValidationUtil#createValidationEngine(org.apache.jena.rdf.model.Model, org.apache.jena.rdf.model.Model, org.topbraid.shacl.validation.ValidationEngineConfiguration)}
	 * @param dataModel
	 * @param shapesModel
	 * @param configuration
	 * @return
	 */
	public static ValidationEngine createValidationEngine(Model dataModel, Model shapesModel, ValidationEngineConfiguration configuration) {

		shapesModel = ValidationUtil.ensureToshTriplesExist(shapesModel);

		// Make sure all sh:Functions are registered
		SHACLFunctions.registerFunctions(shapesModel);

		// Create Dataset that contains both the data model and the shapes model
		// (here, using a temporary URI for the shapes graph)
		URI shapesGraphURI = SHACLUtil.createRandomShapesGraphURI();
		Dataset dataset = ARQFactory.get().getDataset(dataModel);
		dataset.addNamedModel(shapesGraphURI.toString(), shapesModel);

		ShapesGraph shapesGraph = new ShapesGraph(shapesModel);

		ValidationEngine engine = new InstrumentedValidationEngine(dataset, shapesGraphURI, shapesGraph, null);
		engine.setConfiguration(configuration);
		return engine;
	}
}
