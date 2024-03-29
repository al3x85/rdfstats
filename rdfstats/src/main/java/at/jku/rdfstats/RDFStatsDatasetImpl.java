/**
 * Copyright 2008-2009 Institute for Applied Knowledge Processing, Johannes Kepler University Linz
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.jku.rdfstats;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.jku.rdfstats.expr.CoverageBuilder;
import at.jku.rdfstats.expr.ExprUtils;
import at.jku.rdfstats.hist.Histogram;
import at.jku.rdfstats.hist.RDF2JavaMapper;
import at.jku.rdfstats.vocabulary.Stats;

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.sparql.algebra.AlgebraGenerator;
import com.hp.hpl.jena.sparql.algebra.Op;
import com.hp.hpl.jena.sparql.algebra.OpVars;
import com.hp.hpl.jena.sparql.algebra.OpVisitorBase;
import com.hp.hpl.jena.sparql.algebra.OpWalker;
import com.hp.hpl.jena.sparql.algebra.op.Op0;
import com.hp.hpl.jena.sparql.algebra.op.OpAssign;
import com.hp.hpl.jena.sparql.algebra.op.OpBGP;
import com.hp.hpl.jena.sparql.algebra.op.OpConditional;
import com.hp.hpl.jena.sparql.algebra.op.OpDatasetNames;
import com.hp.hpl.jena.sparql.algebra.op.OpDiff;
import com.hp.hpl.jena.sparql.algebra.op.OpDistinct;
import com.hp.hpl.jena.sparql.algebra.op.OpExt;
import com.hp.hpl.jena.sparql.algebra.op.OpFilter;
import com.hp.hpl.jena.sparql.algebra.op.OpGraph;
import com.hp.hpl.jena.sparql.algebra.op.OpGroupAgg;
import com.hp.hpl.jena.sparql.algebra.op.OpJoin;
import com.hp.hpl.jena.sparql.algebra.op.OpLabel;
import com.hp.hpl.jena.sparql.algebra.op.OpLeftJoin;
import com.hp.hpl.jena.sparql.algebra.op.OpList;
import com.hp.hpl.jena.sparql.algebra.op.OpNull;
import com.hp.hpl.jena.sparql.algebra.op.OpOrder;
import com.hp.hpl.jena.sparql.algebra.op.OpPath;
import com.hp.hpl.jena.sparql.algebra.op.OpProcedure;
import com.hp.hpl.jena.sparql.algebra.op.OpProject;
import com.hp.hpl.jena.sparql.algebra.op.OpPropFunc;
import com.hp.hpl.jena.sparql.algebra.op.OpQuadPattern;
import com.hp.hpl.jena.sparql.algebra.op.OpReduced;
import com.hp.hpl.jena.sparql.algebra.op.OpSequence;
import com.hp.hpl.jena.sparql.algebra.op.OpService;
import com.hp.hpl.jena.sparql.algebra.op.OpSlice;
import com.hp.hpl.jena.sparql.algebra.op.OpTable;
import com.hp.hpl.jena.sparql.algebra.op.OpTriple;
import com.hp.hpl.jena.sparql.algebra.op.OpUnion;
import com.hp.hpl.jena.sparql.core.BasicPattern;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import com.hp.hpl.jena.sparql.engine.binding.BindingMap;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprList;
import com.hp.hpl.jena.vocabulary.DC;

/**
 * @author dorgon
 *
 */
public class RDFStatsDatasetImpl extends JavaResourceViewBase implements RDFStatsDataset {
	private static final Logger log = LoggerFactory.getLogger(RDFStatsDatasetImpl.class);

	private static final int MIN = 0;
	private static final int AVG = 1;
	private static final int MAX = 2;
	
	private final RDFStatsModel stats;
	
	/**
	 * constructor, called by RDFStatsModel
	 * 
	 * @param instance
	 */
	protected RDFStatsDatasetImpl(Resource instance, RDFStatsModel stats) {
		super(instance);
		this.stats = stats;
	}

	/**
	 * @return
	 */
	public String getSourceUrl() {
		String url = null;
		model.enterCriticalSection(Lock.READ);
		try {
			Statement s = resource.getProperty(Stats.sourceUrl);
			if (s != null && s.getObject().isLiteral())
				url = s.getString();
			else if (s != null && s.getObject().isResource())
				url = s.getResource().getURI();
		} catch (Exception e) {
			log.error("Failed to get sourceUrl from RDFStatsDataset: " + e);
		} finally {
			model.leaveCriticalSection();
		}
		return url;
	}

	/**
	 * @return the source type URI
	 */
	public String getSourceType() {
		String r = null;
		model.enterCriticalSection(Lock.READ);
		try {
			Statement s = resource.getProperty(Stats.sourceType);
			if (s != null && s.getObject().isResource())
				r = s.getResource().getURI();
		} catch (Exception e) {
			log.error("Failed to get sourceType from RDFStatsDataset: " + e);
		} finally {
			model.leaveCriticalSection();
		}
		return r;
	}
	
	/**
	 * @return
	 */
	public String getCreator() {
		String creator = null;
		model.enterCriticalSection(Lock.READ);
		try {
			creator = resource.getProperty(DC.creator).getString();
		} catch (Exception e) {
			log.error("Failed to get dc:creator from RDFStatsDataset: " + e);
		} finally {
			model.leaveCriticalSection();
		}
		return creator;
	}

	/**
	 * @return
	 */
	public Calendar getCalendar() {
		Calendar date = null;
		model.enterCriticalSection(Lock.READ);
		try {
			Statement s = resource.getProperty(DC.date);
			if (s != null) 
			date = ((XSDDateTime) s.getLiteral().getValue()).asCalendar();
		} catch (Exception e) {
			log.error("Failed to get dc:date calendar from RDFStatsDataset: " + e);
		} finally {
			model.leaveCriticalSection();
		}
		return date;
	}

	/**
	 * @return
	 */
	public Date getDate() {
		Calendar c = getCalendar();
		if (c != null)
			return c.getTime();
		else
			return null;
	}

	
// ###################  GraphStatistics implementation ###################

	public Set<String> getProperties() throws RDFStatsModelException {
		Set<String> set = new HashSet<String>();
		String sourceUrl = getSourceUrl();
		
//		List<String> classes = stats.getPropertyHistogramClasses(sourceUrl);
//		for (String c : classes) {
			List<String> props = stats.getPropertyHistogramProperties(sourceUrl);
			set.addAll(props);
//		}
		
		return set;
	}
	
	/*
	 * (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#getSubjectsTotal()
	 */
	public Integer getSubjectsTotal() throws RDFStatsModelException {
		Integer bnode = getAnonymousSubjectsTotal();
		if (bnode == null)
			return null;
		
		String sourceUrl = getSourceUrl();
		Integer uriSbj = null;
//		for (String c : stats.getSubjectHistogramClassess(sourceUrl)) {
			Histogram<?> sh = stats.getSubjectHistogram(sourceUrl, false);
			if (sh != null)
				uriSbj = sh.getTotalValues();
			
//		}
		if (uriSbj == null)
			return null;
		
		return bnode + uriSbj;
	}

	/* (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#getAnonymousSubjectsTotal()
	 */
	public Integer getAnonymousSubjectsTotal() throws RDFStatsModelException {
		Integer bnode = null;		
		Histogram<?> bh = stats.getSubjectHistogram(getSourceUrl(), true);
		if (bh != null)
			bnode = bh.getTotalValues();
		return bnode;
	}
	
	/*
	 * (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#getURISubjectsTotal()
	 */
	public Integer getURISubjectsTotal() throws RDFStatsModelException {
		Integer bnode = null;		
		Histogram<?> bh = stats.getSubjectHistogram(getSourceUrl(), false);
		if (bh != null)
			bnode = bh.getTotalValues();
		return bnode;
	}
	
	/*
	 * (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#subjectNotExists(java.lang.String)
	 */
	public Boolean subjectNotExists(String uri) throws RDFStatsModelException {
		String sourceUrl = getSourceUrl();
//		for (String c : stats.getSubjectHistogramClassess(sourceUrl)) {
			Histogram<String> sh = stats.getSubjectHistogram(sourceUrl, false);
			if (sh == null)
				return null;
			if (sh.getEstimatedQuantity(uri) > 0) // uri exists in sh
				return false;
//		}
		return true; // not found in any histogram
	}

	/* (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#getTriplesTotal()
	 */
	public Integer getTriplesTotal() throws RDFStatsModelException {
		String sourceUrl = getSourceUrl();
		int total = 0;
		for (String p : stats.getPropertyHistogramProperties(sourceUrl)) {
			for (String range : stats.getPropertyHistogramRanges(sourceUrl, p)) {
				Histogram h = stats.getPropertyHistogram(sourceUrl, p, range);
				total += h.getTotalValues();
			}
		}
		return total;
	}
	
	public Integer triplesForFilteredPattern(Node s, Node p, Node o, ExprList filter) throws RDFStatsModelException {
		return triplesForFilteredPattern(s, p, o, filter, false);
	}

	/**
	 * 
	 * @param s
	 * @param p
	 * @param o
	 * @param filter
	 * @param filterOptimized true if the filter is already optimized (i.e. DeMorgan and distributive law have been applied)
	 * @return
	 * 
	 * filter expressions can be taken into account if:
	 * - the 
	 * @throws RDFStatsModelException
	 */
	@SuppressWarnings("unchecked")
	private Integer triplesForFilteredPattern(Node s, Node p, Node o, ExprList filter, boolean filterOptimized) throws RDFStatsModelException {
		Integer l = null;
		String sourceUrl = getSourceUrl();
		String pURI, rURI;
		Var pVar, oVar;
		Expr pFilterExpr = null, oFilterExpr = null;
		Histogram h;

		// check if URI subject exists
		if (s.isURI()) {
			Boolean notExists = subjectNotExists(s.getURI());
			if (notExists == null)
				return null; // can't say... missing stats
			else if (notExists)
				return 0; // guaranteed zero results cause s doesn't exist
		}
		
		// optimize filter if needed
		if (filter != null && !filter.isEmpty() && !filterOptimized)
			filter = ExprUtils.optimizeFilterExprs(filter);
		
		try {			
			// :p :o
			if (p.isConcrete() && o.isConcrete()) {
				pURI = p.getURI();
				rURI = RDF2JavaMapper.getType(o); // determine range from object
				
				h = stats.getPropertyHistogram(sourceUrl, pURI, rURI);
				if (h != null) {
					int plus = h.getEstimatedQuantity(h.parseNodeValue(o));
					l = (l == null) ? plus : l + plus;
				}
				
				// special case: sgn() - { :s :p :o } either returns 1 triple or nothing
				if (l != null && s.isConcrete())
					return (l > 0) ? 1 : 0;
				
			// :p ?o
			} else if (p.isConcrete() && o.isVariable()) {
				pURI = p.getURI();
				oVar = Var.alloc(o);
				oFilterExpr = getSingleFilterExpression(filter, oVar);
				
				l = 0; // assume if no property histogram exists for p => there is no corresponding data => l = 0
				for (String range : stats.getPropertyHistogramRanges(sourceUrl, pURI)) {
					h = stats.getPropertyHistogram(sourceUrl, pURI, range);

					// filtered estimation
					Integer plus;
					if (oFilterExpr != null) {
						plus = CoverageBuilder.estimate(oFilterExpr, h);
						if (plus == null)
							return null; // early break => n/a
					} else
						plus = h.getTotalValues();
					
					l = (l == null) ? plus : l + plus;
				}
				
			// ?p :o
			} else if (p.isVariable() && o.isConcrete()) {
				rURI = RDF2JavaMapper.getType(o); // determine range from object
				pVar = Var.alloc(p);
				pFilterExpr = (filter != null) ? getSingleFilterExpression(filter, pVar) : null;

				for (String prop : stats.getPropertyHistogramProperties(sourceUrl, rURI)) {
					
					Binding b = null;
					if (pFilterExpr != null) {
						// for each possible property histogram, bind prop URI to p and test the filter
						b = new BindingMap();
						b.add(pVar, Node.createURI(prop));
					}
					
					if (pFilterExpr == null || pFilterExpr.isSatisfied(b, null)) { // null ExecutionContext should be okay, only used by evalSpecial() which is not used normally
						h = stats.getPropertyHistogram(sourceUrl, prop, rURI);
						int plus = h.getEstimatedQuantity(h.parseNodeValue(o));
						l = (l == null) ? plus : l + plus;
					}
				}
				
			// ?p ?o
			} else if (p.isVariable() && o.isVariable()) {
				pVar = Var.alloc(p);
				oVar = Var.alloc(o);
				
				// prepare filters
				if (filter != null) {
					 pFilterExpr = getSingleFilterExpression(filter, pVar);
					 oFilterExpr = getSingleFilterExpression(filter, oVar);
				}
				
				for (String prop : stats.getPropertyHistogramProperties(sourceUrl)) {

					Binding pb = null;
					if (pFilterExpr != null) {
						// for each possible property histogram, bind prop URI to p and test the filter
						pb = new BindingMap();
						pb.add(pVar, Node.createURI(prop));
					}
					
					if (pFilterExpr == null || pFilterExpr.isSatisfied(pb, null)) {
						for (String range : stats.getPropertyHistogramRanges(sourceUrl, prop)) {
							h = stats.getPropertyHistogram(sourceUrl, prop, range);

							// filtered estimation
							Integer plus;
							if (oFilterExpr != null) {
								plus = CoverageBuilder.estimate(oFilterExpr, h);
								if (plus == null)
									return null; // early break => n/a
							} else
								plus = h.getTotalValues();
							
							l = (l == null) ? plus : l + plus;							
						}
					}
				}
				
			} else 
				return null; // invalid triple pattern

			if (l != null && l > 1 && s.isConcrete())
				l = (int) Math.ceil((double) l / getSubjectsTotal()); // if > 0 return at least 1

			return l;
		} catch (Exception e) {
			throw new RDFStatsModelException("Failed to estimate triple pattern cardinality for " + s + " " + p + " " + o + ".", e);
		}
	}

	/**
	 * get all expressions from ExprList that exclusively mention v and return as a conjunctive single Expr
	 * 
	 * @param filter, already optimized
	 * @param v
	 * @return single conjunctive expression containing exclusively Var o and no other vars, otherwise returns null
	 */
	private Expr getSingleFilterExpression(ExprList filter, Var v) {
		if (filter == null)
			return null;
		
		ExprList scoped = new ExprList();
		for (Expr e : filter.getList()) {
			Set<Var> vars = e.getVarsMentioned();
			if (vars.contains(v) && vars.size() == 1) // e exclusively contains v?
				scoped.add(e);
		}

		// flatten into single expression, returns null for empty list
		return ExprUtils.flattenConjunctions(scoped);
	}

	/* (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#triplesForPattern(com.hp.hpl.jena.graph.Node, com.hp.hpl.jena.graph.Node, com.hp.hpl.jena.graph.Node)
	 */
	public Integer triplesForPattern(Node s, Node p, Node o) throws RDFStatsModelException {
		return triplesForFilteredPattern(s, p, o, null);
	}

	/* (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#getPropertyEntropy(java.lang.String)
	 */
	public Float getPropertyEntropy(String p) throws RDFStatsModelException {
		String sourceUrl = getSourceUrl();
		float entropy = 1.0f;
		List<String> ranges = stats.getPropertyHistogramRanges(sourceUrl, p);
		for (String r : ranges) {
			Histogram h = stats.getPropertyHistogram(sourceUrl, p, ranges.get(0));
			entropy *= (float) h.getDistinctValues() / h.getTotalValues();
		}
		return entropy;
	}
	
	/* (non-Javadoc)
	 * @see at.jku.rdfstats.GraphStatistics#getPropertyEntropySorted()
	 */
	public TreeMap<Float, String> getPropertyEntropySorted() throws RDFStatsModelException {
		String sourceUrl = getSourceUrl();
		TreeMap<Float, String> entropies = new TreeMap<Float, String>();
		List<String> props = stats.getPropertyHistogramProperties(sourceUrl);
		for (String p : props)
			entropies.put(getPropertyEntropy(p), p);
		return entropies;
	}
	

// ###################  QueryStatistics implementation ###################
	
	public Integer[] triplesForBGP(BasicPattern bgp) throws RDFStatsModelException {
		Iterator<Triple> it = bgp.iterator();
		Integer l = Integer.MAX_VALUE;
		Integer intermed;
		
		while (it.hasNext()) {
			Triple t = it.next();
			intermed = triplesForPattern(t.getSubject(), t.getPredicate(), t.getObject());
			if (intermed == null)
				return null;
			
			if (intermed < l)
				l = intermed;
		}
			
		return new Integer[] { l, l, l };
	}
	
	public Integer[] triplesForFilteredBGP(BasicPattern bgp, ExprList exprs) throws RDFStatsModelException {
		Set<Var> vars = exprs.getVarsMentioned();
		Iterator<Triple> it = bgp.iterator();
		Integer l = Integer.MAX_VALUE;
		Integer intermed;
		
		while (it.hasNext()) {
			Triple t = it.next();

			if (t.getSubject().isVariable() && vars.contains(Var.alloc(t.getSubject())) ||
				t.getPredicate().isVariable() && vars.contains(Var.alloc(t.getPredicate())) ||
				t.getObject().isVariable() && vars.contains(Var.alloc(t.getObject())))
				intermed = triplesForFilteredPattern(t.getSubject(), t.getPredicate(), t.getObject(), exprs, true);
			else
				intermed = triplesForPattern(t.getSubject(), t.getPredicate(), t.getObject());

			if (intermed == null)
				return null;
			
			if (intermed < l)
				l = intermed;
		}
		
		return new Integer[] { l, l, l };
	}
	
	public Integer[] triplesForQuery(String qry) {
		return triplesForQuery(QueryFactory.create(qry)); // default syntax
	}
	
	public Integer[] triplesForQuery(Query qry) {
		return triplesForQueryPlan(new AlgebraGenerator().compile(qry));
	}
	
	public Integer[] triplesForQueryPlan(Op plan) {
		PlanCalculator pc = new PlanCalculator();
		plan.visit(pc);
		return pc.getEstimatedCardinality();
	}
	
// misc methods
	
	@Override
	public boolean equals(Object o) {
		RDFStatsDatasetImpl other = (RDFStatsDatasetImpl) o;
		return other.getSourceUrl() == getSourceUrl() && other.getSourceType().equals(getSourceType());
//		return resource.equals(
//				((RDFStatsDataset) other)
//					.getWrappedResource());
	}
	
	@Override
	public String toString() {
		return "RDFStats for " + getSourceType() + " <" + getSourceUrl() + ">";
	}
	
	
	/**  plan calculator
	 * 
	 * @author dorgon
	 */
	class PlanCalculator extends OpVisitorBase {
		Integer[] currentEstimate = null;
		Stack<Op> opStack = new Stack<Op>();
		
// Op0
	    public void visit(OpBGP op) {
	    	Integer[] l;
	    	Op prev = null;
	    	if (!opStack.isEmpty())
	    		prev = opStack.peek();
	    	
	    	try {
	    		// TODO push down and merge filters, now we only look at the direct ancestor
		    	if (prev != null && prev instanceof OpFilter)
		    		l = triplesForFilteredBGP(op.getPattern(), ((OpFilter) prev).getExprs());
		    	else
		    		l = triplesForBGP(op.getPattern());
	    	} catch (Exception e) {
	    		throw new RuntimeException("Failed to calculate estimation for " + op.getClass().getName() + "!", e);
	    	}
	    	
	    	currentEstimate = l;
	    }
	    
	    public void visit(OpDatasetNames op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    public void visit(OpNull op) {
	    	currentEstimate = new Integer[] { 0, 0, 0 };
	    }
	    
	    public void visit(OpPath op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!"); 
	    }	    
	    
	    public void visit(OpQuadPattern op) {
	    	Integer[] l;
	    	Op prev = opStack.peek();
	    	try {
		    	if (prev instanceof OpFilter)
		    		l = triplesForFilteredBGP(op.getBasicPattern(), ((OpFilter) prev).getExprs());
		    	else
		    		l = triplesForBGP(op.getBasicPattern());
	    	} catch (Exception e) {
	    		throw new RuntimeException("Failed to calculate estimation for " + op.getClass().getName() + "!", e);
	    	}
	    	
	    	currentEstimate = l;
	    }

	    public void visit(OpTable op) {
	    	//TODO apply previous filter to table?
	    	int l = (int) op.getTable().size();
	    	currentEstimate = new Integer[] { l, l, l };
	    }
	    
	    public void visit(OpTriple op) {
	    	Integer l;
	    	Op prev = opStack.peek();
	    	Triple t = op.getTriple();
	    	try {
		    	if (prev instanceof OpFilter)
		    		l = triplesForFilteredPattern(t.getSubject(), t.getPredicate(), t.getObject(), ((OpFilter) prev).getExprs());
		    	else
		    		l = triplesForPattern(t.getSubject(), t.getPredicate(), t.getObject());
	    	} catch (Exception e) {
	    		throw new RuntimeException("Failed to calculate estimation for " + op.getClass().getName() + "!", e);
	    	}

	    	currentEstimate = new Integer[] { l, l, l };
	    }

// Op1
	    public void visit(OpAssign op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    public void visit(OpFilter op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();
	    	
	    	if (currentEstimate != null && !(op.getSubOp() instanceof Op0)) { // Op0 already uses the filter for BGP estimation
	    		Integer l[] = currentEstimate;
	    		if (l[AVG] > 1)
	    			l[AVG] = (int) Math.ceil((double) l[AVG] / 2); // TODO for the meanwhile assume filter constantly selects 50% in average
	    		
	    		// keep MAX as is
	    		l[MIN] = 0; // assume filter may select nothing
	    		currentEstimate = l;
	    	}	    	
	    }
	    
	    public void visit(OpGraph op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    public void visit(OpLabel op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();
	    }
	    
	    public void visit(OpProcedure op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }

	    public void visit(OpPropFunc op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    public void visit(OpService op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();
	    }
	    
	    // OpModifier (Op1)
	    public void visit(OpDistinct op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();
	    	
	    	Set<Var> unique = getUniqueValueVars(op.getSubOp());
	    	Set<Var> vars = OpVars.patternVars(op.getSubOp());
	    	
	    	// if one of the variables is unique => no reduction, only reduce card if none is unique
	    	if (unique.size() == 0 && vars.size() > 0) { // vars.size() will always be > 0, but check - otherwise we will divide by 0 later
	    		Integer[] l = currentEstimate;
    			l[MIN] = 1;
    			// MAX remains equal
    			
    			// the more variables, the less the chance the estimate is reduced
    			// for 1, 2, 3, ... variables multiply l[AVG] with 0.5, 0.75, 0.875...
    			double mul = 0;
    			double add = .5;
    			for (int i=0; i<vars.size(); i++) {
    				mul += add;
    				add /= 2;
    			}
    			l[AVG] = (int) Math.ceil((double) l[AVG] * mul);
    			
				currentEstimate = l;
	    	}
	    }
	    
	    public void visit(OpReduced op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();
	    	
	    	Set<Var> unique = getUniqueValueVars(op.getSubOp());
	    	Set<Var> vars = OpVars.patternVars(op.getSubOp());
	    	
	    	// if one of the variables is unique => no reduction, only reduce card if none is unique
	    	if (unique.size() == 0 && vars.size() > 0) { // vars.size() will always be > 0, but check - otherwise we will divide by 0 later
	    		Integer[] l = currentEstimate;
    			l[MIN] = 1;
    			// MAX remains equal
    			
    			// the more variables, the less the chance the estimate is reduced
    			// for 1, 2, 3, ... variables multiply l[AVG] with 0.5, 0.75, 0.875...
    			double mul = 0;
    			double add = .5;
    			for (int i=0; i<vars.size(); i++) {
    				mul += add;
    				add /= 2;
    			}
    			l[AVG] = (int) Math.ceil((double) l[AVG] * mul);
    			
				currentEstimate = l;
	    	}
	    }
	    
	    public void visit(OpGroupAgg op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    public void visit(OpList op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    // order doesn't change cardinality
	    public void visit(OpOrder op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();
	    }
	    
	    // projects doesn't change cardinality
	    public void visit(OpProject op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();
	    }
	    
	    public void visit(OpSlice op) {
	    	opStack.push(op);
	    	op.getSubOp().visit(this);
	    	opStack.pop();

	    	Integer[] l = currentEstimate;
	    	if (l != null) { // limit
	    		l[MIN] = Math.min(l[MIN], (int) Math.max(new Integer(Integer.MAX_VALUE).longValue(), op.getLength()));
	    		l[AVG] = Math.min(l[AVG], (int) Math.max(new Integer(Integer.MAX_VALUE).longValue(), op.getLength()));
	    		l[MAX] = Math.min(l[MAX], (int) Math.max(new Integer(Integer.MAX_VALUE).longValue(), op.getLength()));
	    	}

	    	currentEstimate = l;
	    }
	    
	    // Op2
	    public void visit(OpConditional op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    public void visit(OpDiff op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }

	    public void visit(OpJoin op) {
	    	Integer[] l = null;

	    	Op left = op.getLeft();
	    	Op right = op.getRight();

	    	opStack.push(op);
	    	left.visit(this);
	    	Integer[] lc = currentEstimate;
	    	right.visit(this);
	    	Integer[] rc = currentEstimate;
	    	opStack.pop();

	    	if (lc != null && rc != null) {
		    	l = new Integer[3];
		    	
		    	// TODO: check for uniqueness (primary keys?)
		    	
		    	Set<Var> joinVars = OpVars.patternVars(left);
				joinVars.retainAll(OpVars.patternVars(right));
	
				if (joinVars.size() > 0) {
					l[MIN] = 0;
					l[AVG] = (int) Math.ceil((double) (lc[AVG] * rc[AVG]) / 2); // assume 0.5 selectivity
					l[MAX] = lc[MAX] * rc[MAX]; // in case all values left and right are equal
					
				} else { // cross product
					l[MIN] = lc[MIN] * rc[MIN];
					l[AVG] = lc[AVG] * rc[AVG];
					l[MAX] = lc[MAX] * rc[MAX];
				}
			}
	    	
	    	currentEstimate = l;
	    }
	    
	    public void visit(OpLeftJoin op) {
	    	Integer[] l = null;
	    	
	    	Op left = op.getLeft();
	    	Op right = op.getRight();

	    	opStack.push(op);
	    	left.visit(this);
	    	Integer[] lc = currentEstimate;
	    	right.visit(this);
	    	Integer[] rc = currentEstimate;
	    	opStack.pop();

	    	if (lc != null && rc != null) {
		    	l = new Integer[3];
		    	
		    	Set<Var> joinVars = OpVars.patternVars(left);
				joinVars.retainAll(OpVars.patternVars(right));
		
				if (joinVars.size() > 0) {
					l[MIN] = 0;
					l[AVG] = (int) Math.ceil((double) lc[AVG] / 2);
					l[MAX] = lc[MAX];
				
				} else { // cross product
		    		l[MIN] = lc[MIN];
		    		l[AVG] = (int) Math.ceil((double) lc[AVG] / 2);
		    		l[MAX] = lc[MAX];
		    	}
	    	}
	    	
	    	currentEstimate = l;
	    }

	    public void visit(OpUnion op) {
	    	Integer[] l = null;
	    	
	    	opStack.push(op);
	    	op.getLeft().visit(this);
	    	Integer[] left = currentEstimate;
	    	op.getRight().visit(this);
	    	Integer[] right = currentEstimate;
	    	opStack.pop();
	    	
	    	if (left != null && right != null) {
	    		l[MIN] = left[MIN] + right[MIN];
	    		l[AVG] = left[AVG] + right[AVG];
	    		l[MAX] = left[MAX] + right[MAX];	    		
	    	}
	    	
	    	currentEstimate = l;
	    }

	    // OpN
	    public void visit(OpSequence op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }

	    // OpExt
	    public void visit(OpExt op) {
	    	throw new RuntimeException("Estimate not implemented for " + op.getClass().getName() + "!");
	    }
	    
	    public Integer[] getEstimatedCardinality() {
	    	return currentEstimate;
	    }
	}
	
    public Set<Var> getUniqueValueVars(Op op) {
        Set<Var> acc = new HashSet<Var>() ;
        OpWalker.walk(op, new UniqueValueVarCollector(acc)) ;
        return acc ;
    }
    
	class UniqueValueVarCollector extends OpVisitorBase {
        protected Set<Var> acc ;
        
        public UniqueValueVarCollector(Set<Var> acc) { this.acc = acc ; }
        
        @Override
        public void visit(OpBGP opBGP) {
            vars(opBGP.getPattern(), acc) ;
        }
        
        @Override
        public void visit(OpPath opPath) {
        	if (opPath.getTriplePath().isTriple())
        		addVarsFromTriple(acc, opPath.getTriplePath().asTriple());
        }

        @Override
        public void visit(OpQuadPattern quadPattern) {
        	// ignore graph node
            vars(quadPattern.getBasicPattern(), acc) ;
        }

        public void vars(BasicPattern pattern, Collection<Var> acc) {
            for ( Triple triple : pattern )
                addVarsFromTriple(acc, triple) ;
        }
        
        private void addVarsFromTriple(Collection<Var> acc, Triple t) {
        	Node s = t.getSubject();
        	Node p = t.getPredicate();
        	Node o = t.getObject();
        	
        	// a variable is unique if { :s :p ?o } and there is a single histogram with unique values only
        	//                   or if { :s ?p :o } and there is only one histogram for ?p (triple pattern will have cardinality 1 anyway => ignore that case
        	if (s.isVariable() || p.isVariable() || !o.isVariable())
        		return;
        	
        	String sourceUrl = getSourceUrl();
        	String pURI = p.getURI();
        
        	try {
        		List<String> ranges = stats.getPropertyHistogramRanges(sourceUrl, pURI);
        		Var oVar = Var.alloc(o);
        		if (ranges.size() == 1) {
        			Histogram<?> h = stats.getPropertyHistogram(sourceUrl, pURI, ranges.get(0));
        			if (h.getTotalValues() == h.getDistinctValues())
        				acc.add(oVar);
        		}
        	} catch (Exception e) {
        		throw new RuntimeException("Failed to get unique value variables for " + t + "!", e);
        	}
        }
        
	}

}
