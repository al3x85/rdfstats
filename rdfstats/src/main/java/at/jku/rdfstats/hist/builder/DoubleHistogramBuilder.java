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
import java.util.TreeMap;

import at.jku.rdfstats.ParseException;
import at.jku.rdfstats.RDFStatsConfiguration;
import at.jku.rdfstats.hist.DoubleHistogram;
import at.jku.rdfstats.hist.Histogram;

import com.hp.hpl.jena.graph.Node;

/**
 * @author dorgon
 *
 */
public class DoubleHistogramBuilder extends AbstractHistogramBuilder<Double> {

	/**
	 * @param typeUri
	 * @param prefSize
	 */
	public DoubleHistogramBuilder(RDFStatsConfiguration conf, String typeUri, int prefSize) {
		super(conf, typeUri, prefSize);
		
		values = new TreeMap<Double, Integer>();
	}

	public void addNodeValue(Node val) throws HistogramBuilderException {
		try {
			Double d = DoubleHistogram.parseNodeValueImpl(val);
			addValue(d);
		} catch (ParseException e) {
			throw new HistogramBuilderException("Error parsing node value: " + e.getMessage(), e);
		}
	}
	
	/* (non-Javadoc)
	 * @see at.jku.rdfstats.hist.builder.AbstractHistogramBuilder#generateHistogram()
	 */
	@Override
	public Histogram<Double> generateHistogram() {
		Double min = ((TreeMap<Double, Integer>) values).firstKey();
		Double max = ((TreeMap<Double, Integer>) values).lastKey();
		Double range = max-min; // TODO use BigDecimal for calculation
	
		int numBins = (range > 0) ? prefSize : 1;
		if (values.size() < numBins) numBins = values.size();

		double binWidth = range / (double) numBins;
		int[] data = new int[numBins];
		for (Double val : values.keySet()) {
			int idx = (int) Math.floor((val-min) / binWidth);
			if (idx >= data.length) idx = data.length-1; // corner case, last entry fits into last bin even if slightly higher
			data[idx] += values.get(val);
		}
		int distinctValues = values.size();
		values = null;
		
		return (Histogram<Double>) new DoubleHistogram(typeUri, data, distinctValues, min, max, this.getClass());
	}
	
	public void writeData(ByteArrayOutputStream stream, Histogram<Double> hist) {
		HistogramCodec.writeLong(stream, Double.doubleToLongBits(((DoubleHistogram) hist).getMin()));
		HistogramCodec.writeLong(stream, Double.doubleToLongBits(((DoubleHistogram) hist).getMax()));	
		HistogramCodec.writeIntArray(stream, hist.getBinData());
		HistogramCodec.writeInt(stream, hist.getDistinctValues());
	}	
	
	public DoubleHistogram readData(ByteArrayInputStream stream) {
		double min = Double.longBitsToDouble(HistogramCodec.readLong(stream));
		double max = Double.longBitsToDouble(HistogramCodec.readLong(stream));
		int[] bins = HistogramCodec.readIntArray(stream, prefSize);
		int distinctValues = HistogramCodec.readInt(stream);
		
		return new DoubleHistogram(typeUri, bins, distinctValues, min, max, this.getClass());
	}

}
