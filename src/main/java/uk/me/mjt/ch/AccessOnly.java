
package uk.me.mjt.ch;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import uk.me.mjt.ch.Dijkstra.Direction;

public enum AccessOnly {
    TRUE, FALSE;
    
    public static final long ACCESSONLY_START_NODE_ID_PREFIX = 400000000000000000L;
    public static final long ACCESSONLY_END_NODE_ID_PREFIX = 200000000000000000L;
    public static final long INITIAL_NEW_EDGE_ID = 1000000000L;
    
    public static void stratifyMarkedAndImplicitAccessOnlyClusters(MapData allNodes, Node startPoint) {
        markImplicitlyAccessOnlyEdges(allNodes, startPoint);
        stratifyMarkedAccessOnlyClusters(allNodes);
    }
    
    /**
     * Mark edges that, while not tagged access only, you can only get to or
     * can only leave via access only edges.
     * https://www.openstreetmap.org/node/1653073939
     * https://www.openstreetmap.org/node/1499442487
     * https://www.openstreetmap.org/way/151879439
     */
    private static void markImplicitlyAccessOnlyEdges(MapData allNodes, Node startPoint) {
        if (!InaccessibleNodes.findNodesNotBidirectionallyAccessible(allNodes,startPoint).isEmpty()) {
            throw new IllegalArgumentException("We can only mark implicitly access-only edges for graphs "
                    + "where all nodes are bidirectionally accessible. This one doesn't seem to be!");
        }
        
        HashSet<DirectedEdge> accessibleForwards = accessibleEdgesFrom(startPoint, Direction.FORWARDS, AccessOnly.FALSE);
        HashSet<DirectedEdge> accessibleBackwards = accessibleEdgesFrom(startPoint, Direction.BACKWARDS, AccessOnly.FALSE);
        
        HashSet<DirectedEdge> implicitlyAccessOnly = new HashSet();
        for (Node n : allNodes.values()) {
            for (DirectedEdge de : n.edgesFrom) {
                if (accessibleForwards.contains(de) && accessibleBackwards.contains(de)) {
                    // Looks fine to me!
                } else {
                    implicitlyAccessOnly.add(de);
                }
            }
        }
        
        for (DirectedEdge de : implicitlyAccessOnly) {
            if (de.accessOnly == AccessOnly.FALSE) {
                de.accessOnly = AccessOnly.TRUE;
            }
        }
    }
    
    private static HashSet<DirectedEdge> accessibleEdgesFrom(Node startPoint, Direction direction, AccessOnly accessOnly) {
        HashSet<DirectedEdge> result = new HashSet<>();
        HashSet<Node> visited = new HashSet<>();
        TreeSet<Node> toVisit = new TreeSet<>();
        toVisit.add(startPoint);
        
        while (!toVisit.isEmpty()) {
            Node visiting = toVisit.pollFirst();
            visited.add(visiting);
            
            
            Collection<DirectedEdge> toFollow;
            if (direction==Direction.FORWARDS) {
                toFollow=visiting.edgesFrom;
            } else if (direction==Direction.BACKWARDS) {
                toFollow=visiting.edgesTo;
            } else {
                toFollow=new UnionList<>(visiting.edgesFrom,visiting.edgesTo);
            }
            
            for (DirectedEdge de : toFollow ) {
                if (de.accessOnly == accessOnly) {
                    if (!visited.contains(de.to))
                        toVisit.add(de.to);
                    if (!visited.contains(de.from))
                        toVisit.add(de.from);
                    result.add(de);
                }
            }
        }
        
        return result;
    }
    
    public static void stratifyMarkedAccessOnlyClusters(MapData allNodes) {
        List<AccessOnlyCluster> clusters = findAccessOnlyClusters(allNodes.values());
        AtomicLong edgeIdCounter = new AtomicLong(INITIAL_NEW_EDGE_ID);
        
        for (AccessOnlyCluster cluster : clusters) {
            stratifyCluster(allNodes, cluster, edgeIdCounter);
        }
    }
    
    static List<AccessOnlyCluster> findAccessOnlyClusters(Collection<Node> allNodes) {
        Preconditions.checkNoneNull(allNodes);
        
        HashSet<Node> alreadyAssignedToCluster = new HashSet();
        ArrayList<AccessOnlyCluster> clusters = new ArrayList<>();
        
        for (Node n : allNodes) {
            if (n.anyEdgesAccessOnly() && !alreadyAssignedToCluster.contains(n)) {
                AccessOnlyCluster cluster = identifyCluster(n);
                alreadyAssignedToCluster.addAll(cluster.nodes);
                clusters.add(cluster);
            }
        }
        
        return Collections.unmodifiableList(clusters);
    }
    
    static AccessOnlyCluster identifyCluster(Node startPoint) {
        Preconditions.checkNoneNull(startPoint);
        
        AccessOnlyCluster cluster = new AccessOnlyCluster();
        TreeSet<Node> toVisit = new TreeSet<>();
        toVisit.add(startPoint);
        
        while (!toVisit.isEmpty()) {
            Node visiting = toVisit.pollFirst();
            cluster.nodes.add(visiting);
            
            for (DirectedEdge de : accessOnlyEdgesIn(visiting.edgesFrom,visiting.edgesTo) ) {
                if (!cluster.nodes.contains(de.to))
                    toVisit.add(de.to);
                if (!cluster.nodes.contains(de.from))
                    toVisit.add(de.from);
            }
        }
        
        return cluster;
    }
    
    static class AccessOnlyCluster {
        final HashSet<Node> nodes = new HashSet();
    }
    
    private static Collection<DirectedEdge> accessOnlyEdgesIn(List<DirectedEdge> a, List<DirectedEdge> b) {
        return accessOnlyEdgesIn(new UnionList<>(a,b));
    }
    
    private static Collection<DirectedEdge> accessOnlyEdgesIn(Collection<DirectedEdge> toFilter) {
        ArrayList<DirectedEdge> filtered = new ArrayList<>();
        for (DirectedEdge de : toFilter) {
            if (de.accessOnly == AccessOnly.TRUE) {
                filtered.add(de);
            }
        }
        return filtered;
    }
    
    private static void stratifyCluster(MapData allNodes, AccessOnlyCluster cluster, AtomicLong edgeIdCounter) {
        HashMap<Long,Node> startStrata = cloneNodesAndConnectionsAddingPrefix(cluster.nodes, ACCESSONLY_START_NODE_ID_PREFIX, edgeIdCounter);
        HashMap<Long,Node> endStrata = cloneNodesAndConnectionsAddingPrefix(cluster.nodes, ACCESSONLY_END_NODE_ID_PREFIX, edgeIdCounter);
        
        allNodes.addAll(startStrata.values());
        allNodes.addAll(endStrata.values());
        
        linkBordersAndStratas(cluster, startStrata, endStrata, edgeIdCounter);
        removeAccessOnlyEdgesThatHaveBeenReplaced(cluster);
        removeAccessOnlyNodesThatHaveBeenReplaced(allNodes, cluster);
        
        Node.sortNeighborListsAll(startStrata.values());
        Node.sortNeighborListsAll(endStrata.values());
        Node.sortNeighborListsAll(cluster.nodes);
    }
    
    static HashMap<Long,Node> cloneNodesAndConnectionsAddingPrefix(Collection<Node> toClone, long nodeIdPrefix, AtomicLong edgeIdCounter) {
        HashMap<Long,Node> clones = new HashMap<>();
        
        for (Node n : toClone) {
            long newId = n.nodeId+nodeIdPrefix;
            Node clone = new Node(newId, n.lat, n.lon, Barrier.FALSE);
            clones.put(newId, clone);
        }
        
        for (Node n : toClone) {
            for (DirectedEdge de : n.edgesFrom ) {
                if (toClone.contains(de.to)) {
                    long fromId = de.from.nodeId+nodeIdPrefix;
                    long toId = de.to.nodeId+nodeIdPrefix;
                    makeEdgeAndAddToNodes(edgeIdCounter.incrementAndGet(), clones.get(fromId), clones.get(toId), de.driveTimeMs);
                }
            }
        }
        
        Node.sortNeighborListsAll(clones.values());
        return clones;
    }
    
    private static void linkBordersAndStratas(AccessOnlyCluster cluster, HashMap<Long,Node> startStrata, HashMap<Long,Node> endStrata, AtomicLong edgeIdCounter) {
        for (Node n : cluster.nodes) {
            Node startStrataNode = startStrata.get(n.nodeId+ACCESSONLY_START_NODE_ID_PREFIX);
            Node endStrataNode = endStrata.get(n.nodeId+ACCESSONLY_END_NODE_ID_PREFIX);
            if (n.allEdgesAccessOnly()) { // Internal to the cluster
                makeEdgeAndAddToNodes(edgeIdCounter.incrementAndGet(), startStrataNode, endStrataNode, 0);
            } else { // Border to the cluster
                makeEdgeAndAddToNodes(edgeIdCounter.incrementAndGet(), startStrataNode, n, 0);
                makeEdgeAndAddToNodes(edgeIdCounter.incrementAndGet(), n, endStrataNode, 0);
            }
            
        }
    }
    
    private static DirectedEdge makeEdgeAndAddToNodes(long edgeId, Node from, Node to, int driveTimeMs) {
        Preconditions.checkNoneNull(from,to);
        DirectedEdge de = new DirectedEdge(edgeId, from, to, driveTimeMs, AccessOnly.FALSE);
        from.edgesFrom.add(de);
        to.edgesTo.add(de);
        return de;
    }
    
    private static void removeAccessOnlyEdgesThatHaveBeenReplaced(AccessOnlyCluster cluster) {
        for (Node n : cluster.nodes) {
            for (DirectedEdge toRemove : accessOnlyEdgesIn(n.edgesFrom,n.edgesTo)) {
                toRemove.from.edgesFrom.remove(toRemove);
                toRemove.to.edgesTo.remove(toRemove);
            }
        }
    }
    
    private static void removeAccessOnlyNodesThatHaveBeenReplaced(MapData allNodes, AccessOnlyCluster cluster) {
        for (Node n : cluster.nodes) {
            if (n.edgesFrom.isEmpty() && n.edgesTo.isEmpty()) {
                allNodes.removeNodeAndConnectedEdges(n);
            }
        }
    }
}
