package org.antlr.v4.tool;

import org.antlr.v4.Tool;
import org.antlr.v4.automata.*;
import org.antlr.v4.misc.Utils;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupDir;

import java.util.*;

/** The DOT (part of graphviz) generation aspect. */
public class DOTGenerator {
	public static final boolean STRIP_NONREDUCED_STATES = false;

	protected String arrowhead="normal";
	protected String rankdir="LR";

	/** Library of output templates; use <attrname> format */
    public static STGroup stlib = new STGroupDir("org/antlr/v4/tool/templates/dot");

    /** To prevent infinite recursion when walking state machines, record
     *  which states we've visited.  Make a new set every time you start
     *  walking in case you reuse this object.
     */
    protected Set<Integer> markedStates = null;

    protected Grammar grammar;

    /** This aspect is associated with a grammar */
	public DOTGenerator(Grammar grammar) {
		this.grammar = grammar;
	}

    /** Return a String containing a DOT description that, when displayed,
     *  will show the incoming state machine visually.  All nodes reachable
     *  from startState will be included.
     */
	public String getDOT(NFAState startState) {
		if ( startState==null ) {
			return null;
		}
		// The output DOT graph for visualization
		ST dot = null;
		markedStates = new HashSet<Integer>();
		dot = stlib.getInstanceOf("nfa");
		dot.add("startState",
				Utils.integer(startState.stateNumber));
		walkRuleNFACreatingDOT(dot, startState);
		dot.add("rankdir", rankdir);
		return dot.toString();
	}

	public String getDOT(DFAState startState) {
		if ( startState==null ) {
			return null;
		}
		// The output DOT graph for visualization
		ST dot = null;
		markedStates = new HashSet<Integer>();
		dot = stlib.getInstanceOf("dfa");
		dot.add("startState",
				Utils.integer(startState.stateNumber));
		dot.add("useBox",
				Boolean.valueOf(Tool.internalOption_ShowNFAConfigsInDFA));
		walkCreatingDFADOT(dot, (DFAState)startState);
		dot.add("rankdir", rankdir);
		return dot.toString();
	}

	/** Return a String containing a DOT description that, when displayed,
	 *  will show the incoming state machine visually.  All nodes reachable
     *  from startState will be included.
    public String getRuleNFADOT(State startState) {
        // The output DOT graph for visualization
        ST dot = stlib.getInstanceOf("org/antlr/tool/templates/dot/nfa");

        markedStates = new HashSet();
        dot.add("startState",
                Utils.integer(startState.stateNumber));
        walkRuleNFACreatingDOT(dot, startState);
        return dot.toString();
    }
	 */

    /** Do a depth-first walk of the state machine graph and
     *  fill a DOT description template.  Keep filling the
     *  states and edges attributes.
     */
    protected void walkCreatingDFADOT(ST dot,
									  DFAState s)
    {
		if ( markedStates.contains(Utils.integer(s.stateNumber)) ) {
			return; // already visited this node
        }

		markedStates.add(Utils.integer(s.stateNumber)); // mark this node as completed.

        // first add this node
        ST st;
        if ( s.isAcceptState ) {
            st = stlib.getInstanceOf("stopstate");
        }
        else {
            st = stlib.getInstanceOf("state");
        }
        st.add("name", getStateLabel(s));
        dot.add("states", st);

        // make a DOT edge for each transition
		for (int i = 0; i < s.getNumberOfTransitions(); i++) {
			Edge edge = s.transition(i);
			/*
			System.out.println("dfa "+s.dfa.decisionNumber+
				" edge from s"+s.stateNumber+" ["+i+"] of "+s.getNumberOfTransitions());
			*/
			st = stlib.getInstanceOf("edge");
			st.add("label", getEdgeLabel(edge.toString(grammar)));
			st.add("src", getStateLabel(s));
            st.add("target", getStateLabel(edge.target));
			st.add("arrowhead", arrowhead);
            dot.add("edges", st);
            walkCreatingDFADOT(dot, edge.target); // keep walkin'
        }
    }

    /** Do a depth-first walk of the state machine graph and
     *  fill a DOT description template.  Keep filling the
     *  states and edges attributes.  We know this is an NFA
     *  for a rule so don't traverse edges to other rules and
     *  don't go past rule end state.
     */
    protected void walkRuleNFACreatingDOT(ST dot,
                                          NFAState s)
    {
        if ( markedStates.contains(s) ) {
            return; // already visited this node
        }

        markedStates.add(s.stateNumber); // mark this node as completed.

        // first add this node
        ST stateST;
        if ( s instanceof RuleStopState ) {
            stateST = stlib.getInstanceOf("stopstate");
        }
        else {
            stateST = stlib.getInstanceOf("state");
        }
        stateST.add("name", getStateLabel(s));
        dot.add("states", stateST);

        if ( s instanceof RuleStopState )  {
            return; // don't go past end of rule node to the follow states
        }

        // special case: if decision point, then line up the alt start states
        // unless it's an end of block
		if ( s instanceof DecisionState ) {
			GrammarAST n = ((NFAState)s).ast;
			if ( n!=null && s instanceof BlockEndState ) {
				ST rankST = stlib.getInstanceOf("decision-rank");
				NFAState alt = (NFAState)s;
				while ( alt!=null ) {
					rankST.add("states", getStateLabel(alt));
					if ( alt.transition(1) !=null ) {
						alt = (NFAState)alt.transition(1).target;
					}
					else {
						alt=null;
					}
				}
				dot.add("decisionRanks", rankST);
			}
		}

        // make a DOT edge for each transition
		ST edgeST = null;
		for (int i = 0; i < s.getNumberOfTransitions(); i++) {
            Transition edge = (Transition) s.transition(i);
            if ( edge instanceof RuleTransition ) {
                RuleTransition rr = ((RuleTransition)edge);
                // don't jump to other rules, but display edge to follow node
                edgeST = stlib.getInstanceOf("edge");
				if ( rr.rule.g != grammar ) {
					edgeST.add("label", "<"+rr.rule.g.name+"."+rr.rule.name+">");
				}
				else {
					edgeST.add("label", "<"+rr.rule.name+">");
				}
				edgeST.add("src", getStateLabel(s));
				edgeST.add("target", getStateLabel(rr.followState));
				edgeST.add("arrowhead", arrowhead);
                dot.add("edges", edgeST);
				walkRuleNFACreatingDOT(dot, rr.followState);
                continue;
            }
			if ( edge instanceof ActionTransition ) {
				edgeST = stlib.getInstanceOf("action-edge");
			}
			else if ( edge.isEpsilon() ) {
				edgeST = stlib.getInstanceOf("epsilon-edge");
			}
			else {
				edgeST = stlib.getInstanceOf("edge");
			}
			edgeST.add("label", getEdgeLabel(edge.toString(grammar)));
            edgeST.add("src", getStateLabel(s));
			edgeST.add("target", getStateLabel(edge.target));
			edgeST.add("arrowhead", arrowhead);
            dot.add("edges", edgeST);
            walkRuleNFACreatingDOT(dot, edge.target); // keep walkin'
        }
    }

    /** Fix edge strings so they print out in DOT properly;
	 *  generate any gated predicates on edge too.
	 */
    protected String getEdgeLabel(String label) {
		label = Utils.replace(label,"\\", "\\\\");
		label = Utils.replace(label,"\"", "\\\"");
		label = Utils.replace(label,"\n", "\\\\n");
		label = Utils.replace(label,"\r", "");
        return label;
    }

	protected String getStateLabel(NFAState s) {
		if ( s==null ) return "null";
		String stateLabel = String.valueOf(s.stateNumber);
		if ( s instanceof DecisionState ) {
			stateLabel = stateLabel+",d="+((DecisionState)s).decision;
		}
		return '"'+stateLabel+'"';
	}

	protected String getStateLabel(DFAState s) {
		if ( s==null ) return "null";
		String stateLabel = String.valueOf(s.stateNumber);
		StringBuffer buf = new StringBuffer(250);
		buf.append('s');
		buf.append(s.stateNumber);
		if ( Tool.internalOption_ShowNFAConfigsInDFA ) {
			Set<Integer> alts = ((DFAState)s).getAltSet();
			if ( alts!=null ) {
				buf.append("\\n");
				// separate alts
				List<Integer> altList = new ArrayList<Integer>();
				altList.addAll(alts);
				Collections.sort(altList);
				Set configurations = ((DFAState) s).nfaConfigs;
				for (int altIndex = 0; altIndex < altList.size(); altIndex++) {
					Integer altI = (Integer) altList.get(altIndex);
					int alt = altI.intValue();
					if ( altIndex>0 ) {
						buf.append("\\n");
					}
					buf.append("alt");
					buf.append(alt);
					buf.append(':');
					// get a list of configs for just this alt
					// it will help us print better later
					List<NFAConfig> configsInAlt = new ArrayList<NFAConfig>();
					for (Iterator it = configurations.iterator(); it.hasNext();) {
						NFAConfig c = (NFAConfig) it.next();
						if ( c.alt!=alt ) continue;
						configsInAlt.add(c);
					}
					int n = 0;
					for (int cIndex = 0; cIndex < configsInAlt.size(); cIndex++) {
						NFAConfig c =
							(NFAConfig)configsInAlt.get(cIndex);
						n++;
						buf.append(c.toString(false));
						if ( (cIndex+1)<configsInAlt.size() ) {
							buf.append(", ");
						}
						if ( n%5==0 && (configsInAlt.size()-cIndex)>3 ) {
							buf.append("\\n");
						}
					}
				}
			}
		}
		stateLabel = buf.toString();
		if ( s.isAcceptState ) {
            stateLabel = stateLabel+"=>"+s.getUniquelyPredictedAlt();
        }
        return '"'+stateLabel+'"';
    }
}
