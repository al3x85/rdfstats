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

package at.jku.rdfstats.hist.builder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import at.jku.rdfstats.RDFStatsConfiguration;
import at.jku.rdfstats.hist.Histogram;

/**
 * @author dorgon
 *
 * Abstract histogram builder, implements generic methods and adds some abstract methods a concrete builder must implement.
 * 
 * Note that builders of concrete sub-classes need to initialize Map<NATIVE, Long> values in the constructor
 * with an appropriate Map implementation. But they can also use custom data structures for the building process.
 */
public abstract class AbstractHistogramBuilder<NATIVE> implements HistogramBuilder<NATIVE> {
	
	/** the generated histogram (cached after generation) */
	protected Histogram<NATIVE> histogram;
	
	/** all the collected values - may require much of RAM, but allows to generate histograms of unordered distributions */
	protected Map<NATIVE, Integer> values;
	
	/** the data type URI this instance is used for */
	protected String typeUri;
	
	/** the preferred size in number of buckets - may be smaller or bigger as requested if it's feasible/better */
	protected int prefSize;
	
	/** reference to {@link RDFStatsConfiguration} */
	protected RDFStatsConfiguration conf;
	
	/** constructor
	 * 
	 * @param typeUri
	 * @param prefSize
	 */
	public AbstractHistogramBuilder(RDFStatsConfiguration conf, String typeUri, int prefSize) {
		this.conf = conf;
		this.typeUri = typeUri;
		this.prefSize = prefSize;
	}

	/* (non-Javadoc)
	 * @see at.jku.rdfstats.hist.builder.HistogramBuilder#addValue(java.lang.Object)
	 */
	public void addValue(NATIVE val) {
		Integer old = values.get(val);
		if (old == null)
			values.put(val, 1);
		else if (old <= Integer.MAX_VALUE)
			values.put(val, ++old);
	}

	/* (non-Javadoc)
	 * @see at.jku.rdfstats.hist.builder.HistogramBuilder#getHistogram()
	 */
	public final Histogram<NATIVE> getHistogram() {
		if (histogram == null)
			histogram = generateHistogram();
		return histogram;
	}
	
	/**
	 * @return generate and return the histogram, will be cached in field histogram
	 */
	protected abstract Histogram<NATIVE> generateHistogram();
	
	/** must implement byte stream encoding used by {@link HistogramCodec} */
	public abstract Histogram<NATIVE> readData(ByteArrayInputStream in);

	/** must implement byte stream decoding used by {@link HistogramCodec} */
	public abstract void writeData(ByteArrayOutputStream out, Histogram<NATIVE> h);
	
}

