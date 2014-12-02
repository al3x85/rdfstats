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
package at.jku.rdfstats.test.codec;

import junit.framework.TestCase;
import at.jku.rdfstats.hist.OrderedStringHistogram;
import at.jku.rdfstats.hist.builder.HistogramBuilderException;
import at.jku.rdfstats.hist.builder.OrderedStringHistogramBuilder;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;

/**
 * @author dorgon
 *
 */
public class OrderedStringHistogramCodecTest extends TestCase {
	
	public void testOrderedStringHistogramCoding() throws HistogramBuilderException {
		OrderedStringHistogram h = new OrderedStringHistogram(
				XSDDatatype.XSDstring.getURI(),
				new int[] { 203, 30, 1, 2, 10, 100 },
				126,
				new int[] { 20, 1, 1, 2, 2, 100 },
				new String[] { "A", "AB", "C", "F", "Foo", "Voodoo" },
				"A",
				"Voodoo",
				OrderedStringHistogramBuilder.class);
		CodecTest.performCodecTest(h);
		assertEquals("A", h.getMin());
		assertEquals("Voodoo", h.getMax());
		assertEquals(126, h.getDistinctValues());
		assertEquals(2, h.getDistinctBinValues(4));
	}
}
