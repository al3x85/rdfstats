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
import at.jku.rdfstats.hist.FloatHistogram;
import at.jku.rdfstats.hist.Histogram;

import com.hp.hpl.jena.graph.Node;

/**
 * @author dorgon
 *
 */
public class FloatHistogramBuilder extends AbstractHistogramBuilder<Float> {

	/**
	 * @param typeUri
	 * @param prefSize
	 */
	public FloatHistogramBuilder(RDFStatsConfiguration conf, String typeUri, int prefSize) {
		super(conf, typeUri, prefSize);
		
		values = new TreeMap<Float, Integer>();
	}
	
	
	public void addNodeValue(Node val) throws HistogramBuilderException {
		try {
			Float f = FloatHistogram.parseNodeValueImpl(val);
			addValue(f);
		} catch (ParseException e) {
			throw new HistogramBuilderException("Error parsing node value: " + e.getMessage(), e);
		}		
	}
	
	/* (non-Javadoc)
	 * @see at.jku.rdfstats.hist.builder.AbstractHistogramBuilder#generateHistogram()
	 */
	@Override
	public Histogram<Float> generateHistogram() {
		Float min = ((TreeMap<Float, Integer>) values).firstKey();
		Float max = ((TreeMap<Float, Integer>) values).lastKey();
		Float range = max-min;
	
		int numBins = (range > 0) ? prefSize : 1;
		if (values.size() < numBins) numBins = values.size();

		float binWidth = range / numBins;
		int[] data = new int[numBins];
		for (Float val : values.keySet()) {
			int idx = (int) Math.floor((val-min) / binWidth);
			if (idx >= data.length) idx = data.length-1; // corner case, last entry fits into last bin even if slightly higher
			data[idx] += values.get(val);
		}
		int distinctValues = values.size();
		values = null;
		
		return (Histogram<Float>) new FloatHistogram(typeUri, data, distinctValues, min, max, this.getClass());
	}
	
	public void writeData(ByteArrayOutputStream stream, Histogram<Float> hist) {
		HistogramCodec.writeInt(stream, Float.floatToIntBits(((FloatHistogram) hist).getMin()));
		HistogramCodec.writeInt(stream, Float.floatToIntBits(((FloatHistogram) hist).getMax()));	
		HistogramCodec.writeIntArray(stream, hist.getBinData());
		HistogramCodec.writeInt(stream, hist.getDistinctValues());
	}	
	
	public FloatHistogram readData(ByteArrayInputStream stream) {
		float min = Float.intBitsToFloat(HistogramCodec.readInt(stream));
		float max = Float.intBitsToFloat(HistogramCodec.readInt(stream));
		int[] bins = HistogramCodec.readIntArray(stream, prefSize);
		int distinctValues = HistogramCodec.readInt(stream);
		
		return new FloatHistogram(typeUri, bins, distinctValues, min, max, this.getClass());
	}

}
