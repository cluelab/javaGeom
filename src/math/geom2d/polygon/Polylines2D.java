/**
 * 
 */

package math.geom2d.polygon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import math.geom2d.Angle2D;
import math.geom2d.Point2D;
import math.geom2d.Shape2D;
import math.geom2d.conic.Circle2D;
import math.geom2d.conic.CircleArc2D;
import math.geom2d.curve.PolyCurve2D;
import math.geom2d.domain.BoundaryPolyCurve2D;
import math.geom2d.domain.PolyOrientedCurve2D;
import math.geom2d.domain.SmoothOrientedCurve2D;
import math.geom2d.line.LineSegment2D;
import math.geom2d.line.StraightLine2D;
import math.geom2d.point.PointSets2D;

/**
 * Some utility functions for manipulating Polyline2D.
 * 
 * @author dlegland
 * @since 0.6.3
 */
public abstract class Polylines2D {

	/**
	 * Checks if the open polyline has multiple vertices. Polyline extremities
	 * are not tested for equality.
	 */
    public final static boolean hasMultipleVertices(LinearCurve2D polyline) {
    	return hasMultipleVertices(polyline, false);
    }
    
	/**
	 * Checks if the input polyline has multiple vertices. Extremities are
	 * tested if the polyline is closed (second argument is true).
	 */
    public final static boolean hasMultipleVertices(LinearCurve2D polyline, 
    		boolean closed) {
    	// Test vertices within polyline
    	if(PointSets2D.hasAdjacentMultipleVertices(polyline.vertices))
    		return true;
    	
    	// Eventually tests extremities
    	if (closed) {
    		Point2D p1 = polyline.firstPoint();
    		Point2D p2 = polyline.lastPoint();
    		if (p1.distance(p2) < Shape2D.ACCURACY)
    			return true;
    	}
    	
    	return false;
    }
    
    /**
     * Creates a curve parallel to the given polyline, at a distance d. The
     * resulting curve is continuous, but can self-intersect. It is composed of
     * line segments, and circle arcs.
     * 
     * @param polyline the source curve
     * @param d the signed distance between the original curve and its parallel
     * @return the curve parallel to the original curve at a distance d
     * @deprecated use functions in BufferCalculator instead (0.9.1)
     */
    @Deprecated
    public static PolyOrientedCurve2D<SmoothOrientedCurve2D> 
    createParallel(LinearCurve2D polyline, double d) {

        // Collection of parallel curves
        PolyOrientedCurve2D<SmoothOrientedCurve2D> result = 
            new PolyOrientedCurve2D<SmoothOrientedCurve2D>();
        result.setClosed(polyline instanceof LinearRing2D);

        // evacuate degenerate case.
        if (polyline.vertices().size()<2)
            return result;

        // ----- declarations -----

        // vertices of the current edge
        Point2D v1, v2;

        // The corresponding parallel points, and the intersection point
        // for first curve
        Point2D p1, p2, p0 = null;

        // The line parallel to the previous and current line segments
        StraightLine2D line0, line;

        Iterator<Point2D> iterator;

        // ----- Initializations -----

        if (polyline instanceof LinearRing2D) {
            // Add eventually a circle arc, and the first line segment.

            // Extract parallel to last edge
            LineSegment2D lastEdge = polyline.lastEdge();
            line0 = StraightLine2D.createParallel(lastEdge, d);

            v2 = lastEdge.lastPoint();
            p0 = line0.projectedPoint(v2);

            // extract current vertices, and current parallel
            iterator = polyline.vertices().iterator();
            v1 = iterator.next();
            v2 = iterator.next();
            line = new StraightLine2D(v1, v2).parallel(d);

            // Check angle of the 2 lines
            p1 = line.projectedPoint(v1);
            if (Angle2D.angle(line0, line) > Math.PI ^ d < 0) {
                // Line is going to the right -> next line segment will be
                // truncated
                p1 = line.intersection(line0);
                p0 = p1;
            } else {
                // line is going to the left -> add a circle arc
                addCircleArc(result, v1, p0, p1, d);
            }

            p2 = line.projectedPoint(v2);
            line0 = line;
        } else {
            // extract current vertices
            iterator = polyline.vertices().iterator();
            v1 = iterator.next();
            v2 = iterator.next();

            // current parallel
            line0 = new StraightLine2D(v1, v2).parallel(d);
            p1 = line0.projectedPoint(v1);
            p2 = line0.projectedPoint(v2);
        }

        // ----- Main loop -----

        // Main iteration on vertices
        while (iterator.hasNext()) {
            // Compute line parallel to current line segment
            v1 = v2;
            v2 = iterator.next();
            line = new StraightLine2D(v1, v2).parallel(d);

            // Check angle of the 2 lines
            if (Angle2D.angle(line0, line) > Math.PI ^ d < 0) {
                // Line is going to the right -> add the previous line segment
                // truncated at corner
                p2 = line.intersection(line0);
                // TODO: need mode precise control
                result.add(new LineSegment2D(p1, p2));
                p1 = p2;
            } else {
                // line is going to the left -> add the complete line segment
                // and a circle arc
                result.add(new LineSegment2D(p1, p2));
                addCircleArc(result, v1, p2, p1, d);
            }

            // Prepare for next iteration
            p2 = line.projectedPoint(v2);
            line0 = line;
        }

        // ----- Post processing -----

        if (polyline instanceof LinearRing2D) {
            // current line segment join the last point to the first point
            iterator = polyline.vertices().iterator();
            v1 = v2;
            v2 = iterator.next();
            line = new StraightLine2D(v1, v2).parallel(d);

            // Check angle of the 2 lines
            if (Angle2D.angle(line0, line) > Math.PI ^ d < 0) {
                // Line is going to the right -> add the previous line segment
                // truncated at corner
                p2 = line.intersection(line0);
                // TODO: need mode precise control
                result.add(new LineSegment2D(p1, p2));
                p1 = p2;
            } else {
                // line is going to the left -> add the complete line segment
                // and a circle arc
                result.add(new LineSegment2D(p1, p2));
                addCircleArc(result, v1, p2, p1, d);
            }

            // Add the last line segment
            result.add(new LineSegment2D(p1, p0));
        } else {
            // Add the last line segment
            result.add(new LineSegment2D(p1, p2));
        }

        // Return the resulting curve
        return result;
    }

    /**
     * Creates a curve parallel to the given polyline, at a distance d. The
     * resulting curve is continuous, but can self-intersect. It is composed of
     * line segments, and circle arcs.
     * 
     * @param polyline the source curve
     * @param d the signed distance between the original curve and its parallel
     * @return the curve parallel to the original curve at a distance d
     * @deprecated use functions in BufferCalculator instead (0.9.1)
     */
    @Deprecated
    public static BoundaryPolyCurve2D<SmoothOrientedCurve2D>
    createClosedParallel(LinearRing2D polyline, double d) {

        // Collection of parallel curves
        BoundaryPolyCurve2D<SmoothOrientedCurve2D> result = 
            new BoundaryPolyCurve2D<SmoothOrientedCurve2D>();
        result.setClosed(true);

        // evacuate degenerate case.
        if (polyline.vertices().size() < 2)
            return result;

        // ----- declarations -----

        // vertices of the current edge
        Point2D v1, v2;

        // The corresponding parallel points, and the intersection point
        // for first curve
        Point2D p1, p2, p0 = null;

        // The line parallel to the previous and current line segments
        StraightLine2D line0, line;

        Iterator<Point2D> iterator;

        // ----- Initializations -----

        // Add eventually a circle arc, and the first line segment.

        // Extract parallel to last edge
        LineSegment2D lastEdge = polyline.lastEdge();
        line0 = StraightLine2D.createParallel(lastEdge, d);

        v2 = lastEdge.lastPoint();
        p0 = line0.projectedPoint(v2);

        // extract current vertices, and current parallel
        iterator = polyline.vertices().iterator();
        v1 = iterator.next();
        v2 = iterator.next();
        line = new StraightLine2D(v1, v2).parallel(d);

        // Check angle of the 2 lines
        p1 = line.projectedPoint(v1);
        if (Angle2D.angle(line0, line) > Math.PI ^ d < 0) {
            // Line is going to the right -> next line segment will be
            // truncated
            p1 = line.intersection(line0);
            p0 = p1;
        } else {
            // line is going to the left -> add a circle arc
            addCircleArc(result, v1, p0, p1, d);
        }

        p2 = line.projectedPoint(v2);
        line0 = line;

        // ----- Main loop -----

        // Main iteration on vertices
        while (iterator.hasNext()) {
            // Compute line parallel to current line segment
            v1 = v2;
            v2 = iterator.next();
            line = new StraightLine2D(v1, v2).parallel(d);

            // Check angle of the 2 lines
            if (Angle2D.angle(line0, line) > Math.PI ^ d < 0) {
                // Line is going to the right -> add the previous line segment
                // truncated at corner
                p2 = line.intersection(line0);
                // TODO: need mode precise control
                result.add(new LineSegment2D(p1, p2));
                p1 = p2;
            } else {
                // line is going to the left -> add the complete line segment
                // and a circle arc
                result.add(new LineSegment2D(p1, p2));
                addCircleArc(result, v1, p2, p1, d);
            }

            // Prepare for next iteration
            p2 = line.projectedPoint(v2);
            line0 = line;
        }

        // ----- Post processing -----

        // current line segment join the last point to the first point
        iterator = polyline.vertices().iterator();
        v1 = v2;
        v2 = iterator.next();
        line = new StraightLine2D(v1, v2).parallel(d);

        // Check angle of the 2 lines
        if (Angle2D.angle(line0, line) > Math.PI ^ d < 0) {
            // Line is going to the right -> add the previous line segment
            // truncated at corner
            p2 = line.intersection(line0);
            // TODO: need mode precise control
            result.add(new LineSegment2D(p1, p2));
            p1 = p2;
        } else {
            // line is going to the left -> add the complete line segment
            // and a circle arc
            result.add(new LineSegment2D(p1, p2));
            addCircleArc(result, v1, p2, p1, d);
        }

        // Add the last line segment
        result.add(new LineSegment2D(p1, p0));

        // Return the resulting curve
        return result;
    }

    /**
     * Adds a new circle arc to the curve set.
     * The new circle arc is defined by a center, two points on the circle that
     * gives the start angle and angle extent of the arc, and a distance that
     * gives both the circle radius and the arc orientation.
     */
    private static void addCircleArc(PolyCurve2D<SmoothOrientedCurve2D> result, 
    		Point2D v1, Point2D p1, Point2D p2, double d) {
        Circle2D circle = new Circle2D(v1, Math.abs(d));
        double t0 = circle.position(p1);
        double t1 = circle.position(p2);
        result.add(new CircleArc2D(v1, Math.abs(d), t0, t1, d>0));
    }
    
    /**
     * Return all intersection points between the 2 polylines.
     * This method implements a naive algorithm, that tests all possible edge
     * couples.
     * It is supposed that only one point is returned by intersection.
     * @param poly1 a first polyline
     * @param poly2 a second polyline
     * @return the set of intersection points
     */
    public static Collection<Point2D> intersect(
    		LinearCurve2D poly1, LinearCurve2D poly2) {
    	// array for storing intersections
        ArrayList<Point2D> points = new ArrayList<Point2D>();
        
        // iterate on edge couples
        Point2D point;
        for (LineSegment2D edge1 : poly1.edges()) {
            for (LineSegment2D edge2 : poly2.edges()) {
            	// if the intersection is not empty, add it to the set
                point = edge1.intersection(edge2);
                if (point != null) {
                	// we keep only one intersection by couple
                	if (!points.contains(point))
                		points.add(point);
                }
            }
        }

        return points;
    }
}