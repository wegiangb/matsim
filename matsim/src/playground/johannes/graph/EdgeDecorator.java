/* *********************************************************************** *
 * project: org.matsim.*
 * EdgeDecorator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 * 
 */
package playground.johannes.graph;

/**
 * @author illenberger
 *
 */
public class EdgeDecorator<E extends Edge> extends SparseEdge {

	private E delegate;
	
	/**
	 * @param v1
	 * @param v2
	 */
	public EdgeDecorator(VertexDecorator<?> v1, VertexDecorator<?> v2) {
		super(v1, v2);
	}
	
	public void setDelegate(E delegate) {
		this.delegate = delegate;
	}
	
	public E getDelegate() {
		return delegate;
	}
}
