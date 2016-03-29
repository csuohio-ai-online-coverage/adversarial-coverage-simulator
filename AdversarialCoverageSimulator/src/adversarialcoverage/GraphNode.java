package adversarialcoverage;

import java.util.ArrayList;
import java.util.List;

public class GraphNode extends Node {
	List<GraphEdge> edges = new ArrayList<>();
	NodeType type;


	public GraphNode(List<GraphEdge> edges, NodeType nodeType) {
		this.edges.addAll(edges);
		this.type = nodeType;
	}
}


/**
 * Represents a directed edge in a graph.
 * 
 * @author Mike D'Arcy
 *
 */
class GraphEdge {
	double cost = 0.0;
	GraphNode node;


	public GraphEdge(double cost, GraphNode node) {
		this.cost = cost;
		this.node = node;
	}
}
