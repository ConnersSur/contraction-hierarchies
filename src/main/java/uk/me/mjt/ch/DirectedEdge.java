package uk.me.mjt.ch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectedEdge implements Comparable<DirectedEdge>{
    public static final long PLACEHOLDER_ID = -123456L;
    
    public final long edgeId;
    public final Node from;
    public final Node to;
    public final int driveTimeMs;
    //public final boolean isAccessOnly;
    public AccessOnly accessOnly;

    // Parameters for graph contraction:
    public final DirectedEdge first;
    public final DirectedEdge second;
    public final int contractionDepth;
    private final UnionList<DirectedEdge> uncontractedEdges;
    
    public DirectedEdge(long edgeId, Node from, Node to, int driveTimeMs, AccessOnly isAccessOnly) {
        this(edgeId,from,to,driveTimeMs,isAccessOnly,null,null);
    }
    
    public DirectedEdge(long edgeId, Node from, Node to, int driveTimeMs, DirectedEdge first, DirectedEdge second) {
        this(edgeId, from, to, driveTimeMs, null, first, second);
    }

    private DirectedEdge(long edgeId, Node from, Node to, int driveTimeMs, AccessOnly accessOnly, DirectedEdge first, DirectedEdge second) {
        Preconditions.checkNoneNull(from, to);
        Preconditions.require(edgeId>0||edgeId==PLACEHOLDER_ID, driveTimeMs >= 0);
        if (edgeId>0 && first!=null && second!=null) {
            // If this check starts failing, your edge IDs for shortcuts probably start too low.
            Preconditions.require(edgeId>first.edgeId, edgeId>second.edgeId);
        }
        this.edgeId = edgeId;
        this.from = from;
        this.to = to;
        this.driveTimeMs = driveTimeMs;
        this.first = first;
        this.second = second;
        if (first == null && second == null) {
            contractionDepth = 0;
            uncontractedEdges = null;
            Preconditions.checkNoneNull(accessOnly);
            this.accessOnly = accessOnly;
        } else if (first != null && second != null){
            contractionDepth = Math.max(first.contractionDepth, second.contractionDepth)+1;
            uncontractedEdges = new UnionList<>(first.getUncontractedEdges(),second.getUncontractedEdges());
            // Eliminate access only nodes edges before performing contraction.
            Preconditions.require(first.accessOnly==AccessOnly.FALSE,second.accessOnly==AccessOnly.FALSE); 
            this.accessOnly = AccessOnly.FALSE;
        } else {
            throw new IllegalArgumentException("Must have either both or neither child edges set. Instead had " + first + " and " + second);
        }
    }
    
    public boolean isShortcut() {
        return (contractionDepth != 0);
    }

    public List<DirectedEdge> getUncontractedEdges() {
        if (!isShortcut()) {
            return Collections.singletonList(this);
        } else {
            return uncontractedEdges;
        }
    }
    
    /*public List<DirectedEdge> getUncontractedEdges() {
        ArrayList<DirectedEdge> result = new ArrayList<>(4000);
        appendUncontractedEdges(result);
        result.trimToSize();
        return Collections.unmodifiableList(result);
    }
    
    private void appendUncontractedEdges(List<DirectedEdge> toAppend) {
        if (!isShortcut()) {
            toAppend.add(this);
        } else {
            first.appendUncontractedEdges(toAppend);
            second.appendUncontractedEdges(toAppend);
        }
    }*/
    
    
    
    public DirectedEdge cloneWithEdgeId(long edgeId) {
        return new DirectedEdge(edgeId, from, to, driveTimeMs, accessOnly, first, second);
    }
    
    public String toString() {
        return from.nodeId+"--"+driveTimeMs+"("+contractionDepth+")-->"+to.nodeId;
    }

    @Override
    public int compareTo(DirectedEdge o) {
        if (o==null) return -1;
        if (this.edgeId==PLACEHOLDER_ID || o.edgeId==PLACEHOLDER_ID) {
            throw new RuntimeException("Michael didn't write a very thorough comparator.");
        }
        return Long.compare(this.edgeId, o.edgeId);
    }
    
}
