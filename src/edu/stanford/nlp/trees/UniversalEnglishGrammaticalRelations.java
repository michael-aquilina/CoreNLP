// Universal Stanford Dependencies - Code for producing and using Universal Stanford dependencies.
// Copyright © 2005-2014 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    parser-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/stanford-dependencies.shtml

package edu.stanford.nlp.trees;

import static edu.stanford.nlp.trees.EnglishPatterns.*;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.util.Generics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static edu.stanford.nlp.trees.GrammaticalRelation.*;


/**
 * <code>UniversalEnglishGrammaticalRelations</code> is a
 * set of {@link GrammaticalRelation} objects according to the Universal
 * Dependencies standard.
 * <p/>
 * Grammatical relations can either be shown in their basic form, where each
 * input token receives a relation, or "collapsed" which does certain normalizations
 * which group words or turns them into relations. See
 * {@link EnglishGrammaticalStructure}.  What is presented here mainly
 * shows the basic form, though there is some mixture. The "collapsed" grammatical
 * relations primarily differ as follows:
 * <ul>
 * <li>Some multiword conjunctions and prepositions are treated as single
 * words, and then processed as below.</li>
 * <li>Prepositions do not appear as words but are turned into new "prep" or "prepc"
 * grammatical relations, one for each preposition.</li>
 * <li>Conjunctions do not appear as words but are turned into new "conj"
 * grammatical relations, one for each conjunction.</li>
 * <li>The possessive "'s" is deleted, leaving just the relation between the
 * possessor and possessum.</li>
 * <li>Agents of passive sentences are recognized and marked as agent and not as prep_by.</li>
 * </ul>
 * <p/>
 * This set of English grammatical relations is not intended to be
 * exhaustive or immutable.  It's just where we're at now.
 * <p/>
 * <p/>
 * See {@link GrammaticalRelation} for details of fields and matching.
 * <p/>
 * <p/>
 * If using LexicalizedParser, it should be run with the
 * <code>-retainTmpSubcategories</code> option and one of the
 * <code>-splitTMP</code> options (e.g., <code>-splitTMP 1</code>) in order to
 * get the temporal NP dependencies maximally right!
 * <p/>
 * <i>Implementation notes: </i> Don't change the set of GRs without discussing it
 * with people first.  If a change is needed, to add a new grammatical relation:
 * <ul>
 * <li> Governor nodes of the grammatical relations should be the lowest ones.</li>
 * <li> Check the semantic head rules in UniversalSemanticHeadFinder and
 * ModCollinsHeadFinder, both in the trees package. That's what will be used to
 * match here.</li>
 * <li> Create and define the GrammaticalRelation similarly to the others.</li>
 * <li> Add it to the <code>values</code> array at the end of the file.</li>
 * </ul>
 * The patterns in this code assume that an NP may be followed by either a
 * -ADV or -TMP functional tag but there are no other functional tags represented.
 * This corresponds to what we currently get from NPTmpRetainingTreeNormalizer or
 * DependencyTreeTransformer.
 *
 * @author Bill MacCartney
 * @author Marie-Catherine de Marneffe
 * @author Christopher Manning
 * @author Galen Andrew (refactoring English-specific stuff)
 * @author Sebastian Schuster
 * @see GrammaticalStructure
 * @see GrammaticalRelation
 * @see EnglishGrammaticalStructure
 */

public class UniversalEnglishGrammaticalRelations {

  //todo: Things still to fix: comparatives, in order to clauses, automatic Vadas-like NP structure

  /** This class is just a holder for static classes
   *  that act a bit like an enum.
   */
  private UniversalEnglishGrammaticalRelations() {}

  // By setting the HeadFinder to null, we find out right away at
  // runtime if we have incorrectly set the HeadFinder for the
  // dependency tregexes
  private static final TregexPatternCompiler tregexCompiler = new TregexPatternCompiler((HeadFinder) null);

  /**
   * The "predicate" grammatical relation.  The predicate of a
   * clause is the main VP of that clause; the predicate of a
   * subject is the predicate of the clause to which the subject
   * belongs.<p>
   * <p/>
   * Example: <br/>
   * "Reagan died" &rarr; <code>pred</code>(Reagan, died)
   */
  public static final GrammaticalRelation PREDICATE =
    new GrammaticalRelation(Language.UniversalEnglish, "pred", "predicate",
        DEPENDENT, "S|SINV", tregexCompiler,
        "S|SINV <# VP=target");


  /**
   * The "auxiliary" grammatical relation.  An auxiliary of a clause is a
   * non-main verb of the clause.<p>
   * <p/>
   * Example: <br/>
   * "Reagan has died" &rarr; <code>aux</code>(died, has)
   */
  public static final GrammaticalRelation AUX_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "aux", "auxiliary",
        DEPENDENT, "VP|SQ|SINV|CONJP", tregexCompiler,
        "VP < VP < (/^(?:TO|MD|VB.*|AUXG?|POS)$/=target)",
        "SQ|SINV < (/^(?:VB|MD|AUX)/=target $++ /^(?:VP|ADJP)/)",
        //move to mwe: "CONJP < TO=target < VB", // (CONJP not to mention)
        // add handling of tricky VP fronting cases...
        "SINV < (VP=target < (/^(?:VB|AUX|POS)/ < " + beAuxiliaryRegex + ") $-- (VP < VBG))");


  /**
    * The "passive auxiliary" grammatical relation. A passive auxiliary of a
    * clause is a
    * non-main verb of the clause which contains the passive information.
    * <p/>
    * Example: <br/>
    * "Kennedy has been killed" &rarr; <code>auxpass</code>(killed, been)
    */
  public static final GrammaticalRelation AUX_PASSIVE_MODIFIER =
     new GrammaticalRelation(Language.UniversalEnglish, "auxpass", "passive auxiliary",
         AUX_MODIFIER, "VP|SQ|SINV", tregexCompiler,
         "VP < (/^(?:VB|AUX|POS)/=target < " + passiveAuxWordRegex + " ) < (VP|ADJP [ < VBN|VBD | < (VP|ADJP < VBN|VBD) < CC ] )",
         "SQ|SINV < (/^(?:VB|AUX|POS)/=target < " + beAuxiliaryRegex + " $++ (VP < VBD|VBN))",
         // add handling of tricky VP fronting cases...
         "SINV < (VP=target < (/^(?:VB|AUX|POS)/ < " + beAuxiliaryRegex + ") $-- (VP < VBD|VBN))",
         "SINV < (VP=target < (VP < (/^(?:VB|AUX|POS)/ < " + beAuxiliaryRegex + ")) $-- (VP < VBD|VBN))");

  /**
   * The "copula" grammatical relation.  A copula is the relation between
   * the complement of a copular verb and the copular verb.<p>
   * <p/>
   * Examples: <br/>
   * "Bill is big" &rarr; <code>cop</code>(big, is) <br/>
   * "Bill is an honest man" &rarr; <code>cop</code>(man, is)
   */
  public static final GrammaticalRelation COPULA =
    new GrammaticalRelation(Language.UniversalEnglish, "cop", "copula",
        AUX_MODIFIER, "VP|SQ|SINV|SBARQ", tregexCompiler,
        "VP < (/^(?:VB|AUX)/=target < " + copularWordRegex + " [ $++ (/^(?:ADJP|NP$|WHNP$)/ !< (VBN|VBD !$++ /^N/)) | $++ (S <: (ADJP < JJ)) ] )",
        "SQ|SINV < (/^(?:VB|AUX)/=target < " + copularWordRegex + " [ $++ (ADJP !< VBN|VBD) | $++ (NP $++ NP) | $++ (S <: (ADJP < JJ)) ] )",
        // matches (what, is) in "what is that" after the SQ has been flattened out of the tree
        "SBARQ < (/^(?:VB|AUX)/=target < " + copularWordRegex + ") < (WHNP < WP)",
        // "Such a great idea this was"
        "SINV <# (NP $++ (NP $++ (VP=target < (/^(?:VB|AUX)/ < " + copularWordRegex + "))))");


  private static final String ETC_PAT = "(FW < /^(?i:etc)$/)";
  private static final String ETC_PAT_target = "(FW=target < /^(?i:etc)$/)";

  private static final String FW_ETC_PAT = "(ADVP|NP <1 (FW < /^(?i:etc)$/))";
  private static final String FW_ETC_PAT_target = "(ADVP|NP=target <1 (FW < /^(?i:etc)$/))";

  // match "not", "n't", "nt" (for informal writing), or "never" as _complete_ string
  private static final String NOT_PAT = "/^(?i:n[o']?t|never)$/";

  private static final String WESTERN_SMILEY = "/^(?:[<>]?[:;=8][\\-o\\*']?(?:-RRB-|-LRB-|[DPdpO\\/\\\\\\:}{@\\|\\[\\]])|(?:-RRB-|-LRB-|[DPdpO\\/\\\\\\:}{@\\|\\[\\]])[\\-o\\*']?[:;=8][<>]?)$/";

  private static final String ASIAN_SMILEY = "/(?!^--$)^(?:-LRB-)?[\\-\\^x=~<>'][_.]?[\\-\\^x=~<>'](?:-RRB-)?$/";

  /**
   * The "conjunct" grammatical relation.  A conjunct is the relation between
   * two elements connected by a conjunction word.  We treat conjunctions
   * asymmetrically: The head of the relation is the first conjunct and other
   * conjunctions depend on it via the <i>conj</i> relation.<p>
   * <p/>
   * Example: <br/>
   * "Bill is big and honest" &rarr; <code>conj</code>(big, honest)
   * <p/>
   * <i>Note:</i>Modified in 2010 to exclude the case of a CC/CONJP first in its phrase: it has to conjoin things.
   */
  public static final GrammaticalRelation CONJUNCT =
    new GrammaticalRelation(Language.UniversalEnglish, "conj", "conjunct",
        DEPENDENT, "VP|(?:WH)?NP(?:-TMP|-ADV)?|ADJP|PP|QP|ADVP|UCP(?:-TMP|-ADV)?|S|NX|SBAR|SBARQ|SINV|SQ|JJP|NML|RRC", tregexCompiler,
            "VP|S|SBAR|SBARQ|SINV|SQ|RRC < (CC|CONJP $-- !/^(?:``|-LRB-|PRN|PP|ADVP|RB|MWE)/ $+ !/^(?:SBAR|PRN|``|''|-[LR]RB-|,|:|\\.)$/=target)",
            // This case is separated out from the previous case to
            // avoid conflicts with advcl when you have phrases such as
            // "but only because ..."
            "SBAR < (CC|CONJP $-- @SBAR $+ @SBAR=target)",
            // non-parenthetical or comma in suitable phrase with conj then adverb to left
            "VP|S|SBAR|SBARQ|SINV|SQ|RRC < (CC|CONJP $-- !/^(?:``|-LRB-|PRN|PP|ADVP|RB)/ $+ (ADVP $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target))",
            // content phrase to the right of a comma or a parenthetical
            // The test at the end is to make sure that a conjunction or
            // comma etc actually show up between the target of the conj
            // dependency and the head of the phrase.  Otherwise, a
            // different relationship is probably more appropriate.
            // Note that this test looks for one of two things: a
            // cc/conjp which does not have a , between it and the
            // target or a , which does not appear to the right of a
            // cc/conjp.  This test eliminates things such as
            // parenthetics which come after a list, such as in the
            // sentence "to see the market go down and dump everything,
            // which ..." where "go down and dump everything, which..."
            // is all in one VP node.
            "VP|S|SBAR|SBARQ|SINV|SQ=root < (CC|CONJP $-- !/^(?:``|-LRB-|PRN|PP|ADVP|RB)/) < (/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/ $+ (/^S|SINV$|^(?:A|N|V|PP|PRP|J|W|R)/=target [$-- (CC|CONJP $-- (__ ># =root) !$++ (/^:|,$/ $++ =target)) | $-- (/^:|,$/ $-- (__ ># =root) [!$-- /^CC|CONJP$/ | $++ (=target < (/^,$/ $++ (__ ># =target)))])] ) )",

            // non-parenthetical or comma in suitable phrase with conjunction to left
            "/^(?:ADJP|JJP|PP|QP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ [ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN)$/ $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target) | < " + ETC_PAT_target + " | < " + FW_ETC_PAT_target + "]",
            // non-parenthetical or comma in suitable phrase with conj then adverb to left
            "/^(?:ADJP|PP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN)$/ $+ (ADVP $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target))",
            // content phrase to the right of a comma or a parenthetical
            "/^(?:ADJP|PP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ [ < (CC|CONJP $-- !/^(?:``|-LRB-|PRN)$/) | < " + ETC_PAT + " | < " + FW_ETC_PAT + "] < (/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/ [ $+ /^S|SINV$|^(?:A|N|V|PP|PRP|J|W|R)/=target | $+ " + ETC_PAT_target + " ] )",

            // content phrase to the left of a comma for at least NX
            "NX|NML [ < (CC|CONJP $- __) | < " + ETC_PAT + "] < (/^,$/ $- /^(?:A|N|V|PP|PRP|J|W|R|S)/=target)",
            // to take the conjunct in a preconjunct structure "either X or Y"
            // also catches some missing examples of etc as conj
            "/^(?:VP|S|SBAR|SBARQ|SINV|ADJP|PP|QP|(?:WH)?NP(?:-TMP|-ADV)?|ADVP|UCP(?:-TMP|-ADV)?|NX|NML)$/ [ < (CC $++ (CC|CONJP $+ !/^(?:PRN|``|''|-[LR]RB-|,|:|\\.)$/=target)) | <- " + ETC_PAT_target + " | <- " + FW_ETC_PAT_target + " ]");


  //TODO: do something with "if not"???
  //TODO: What to do with "not only" etc.
  /**
   * The "coordination" grammatical relation.  A coordination is the relation
   * between an element and a conjunction.
   * <p/>
   * Example: <br/>
   * "Bill is big and honest." &rarr; <code>cc</code>(big, and)
   */
  public static final GrammaticalRelation COORDINATION =
    new GrammaticalRelation(Language.UniversalEnglish, "cc", "coordination",
        DEPENDENT, ".*", tregexCompiler,
            "__ ([ < (CC=target !< /^(?i:either|neither|both)$/ ) | < (CONJP=target !< (RB < /^(?i:not)$/ $+ (RB|JJ < /^(?i:only|just|merely)$/))) ] [!> /PP/ | !>2 NP])");
  

  /**
   * The "punctuation" grammatical relation.  This is used for any piece of
   * punctuation in a clause, if punctuation is being retained in the
   * typed dependencies.
   * <p/>
   * Example: <br/>
   * "Go home!" &rarr; <code>punct</code>(Go, !)
   * <p/>
   * The condition for NFP to appear hear is that it does not match the emoticon patterns under discourse.
   */
  public static final GrammaticalRelation PUNCTUATION =
    new GrammaticalRelation(Language.UniversalEnglish, "punct", "punctuation",
        DEPENDENT, ".*", tregexCompiler,
            "__ < /^(?:\\.|:|,|''|``|\\*|-LRB-|-RRB-|HYPH)$/=target",
            "__ < (NFP=target !< " + WESTERN_SMILEY + " !< " + ASIAN_SMILEY + ")");


  /**
   * The "argument" grammatical relation.  An argument of a VP is a
   * subject or complement of that VP; an argument of a clause is
   * an argument of the VP which is the predicate of that
   * clause.<p>
   * <p/>
   * Example: <br/>
   * "Clinton defeated Dole" &rarr; <code>arg</code>(defeated, Clinton), <code>arg</code>(defeated, Dole)
   */
  public static final GrammaticalRelation ARGUMENT =
    new GrammaticalRelation(Language.UniversalEnglish, "arg", "argument", DEPENDENT);


  /**
   * The "subject" grammatical relation.  The subject of a VP is
   * the noun or clause that performs or experiences the VP; the
   * subject of a clause is the subject of the VP which is the
   * predicate of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "Clinton defeated Dole" &rarr; <code>subj</code>(defeated, Clinton) <br/>
   * "What she said is untrue" &rarr; <code>subj</code>(is, What she said)
   */
  public static final GrammaticalRelation SUBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "subj", "subject", ARGUMENT);


  /**
   * The "nominal subject" grammatical relation.  A nominal subject is
   * a subject which is an noun phrase.<p>
   * <p/>
   * Example: <br/>
   * "Clinton defeated Dole" &rarr; <code>nsubj</code>(defeated, Clinton)
   */
  public static final GrammaticalRelation NOMINAL_SUBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "nsubj", "nominal subject",
        SUBJECT, "S|SQ|SBARQ|SINV|SBAR|PRN", tregexCompiler,
            "S=subj < ((NP|WHNP=target !< EX !<# (/^NN/ < (" + timeWordRegex + "))) $++ VP=verb) : (=subj !> VP | !<< (=verb < TO))",
            "S < ( NP=target <# (/^NN/ < " + timeWordRegex + ") !$++ NP $++VP)",
            "SQ|PRN < (NP=target !< EX $++ VP)",
            "SQ < (NP=target !< EX $- (/^(?:VB|AUX)/ < " + copularWordRegex + ") !$++ VP)",
            // Allows us to match "Does it?" without matching "Who does it?"
            "SQ < (NP=target !< EX $- /^(?:VB|AUX)/ !$++ VP) !$-- NP|WHNP",
            "SQ < ((NP=target !< EX) $- (RB $- /^(?:VB|AUX)/) ![$++ VP])",
            "SBARQ < WHNP=target < (SQ < (VP !$-- NP))",
            // This will capture incorrectly parsed trees in sentences
            // such as "What disease causes cancer" without capturing
            // correctly parsed trees such as "What do elephants eat?"
            "SBARQ < WHNP=target < (SQ < ((/^(?:VB)/ !< " + copularWordRegex + ") !$-- NP !$++ VP))",
            "SBARQ < (SQ=target < (/^(?:VB|AUX)/ < " + copularWordRegex + ") !< VP)",
            // matches subj in SINV
            "SINV < (NP|WHNP=target [ $- VP|VBZ|VBD|VBP|VB|MD|AUX | $- (@RB|ADVP $- VP|VBZ|VBD|VBP|VB|MD|AUX) | !$- __ !$ @NP] )",
            // Another SINV subj, such as "Such a great idea this was"
            "SINV < (NP $++ (NP=target $++ (VP < (/^(?:VB|AUX)/ < " + copularWordRegex + "))))",
            //matches subj in xcomp like "He considered him a friend"
            "S < (NP=target $+ NP|ADJP) > VP",
            // matches subj in relative clauses
            "SBAR < WHNP=target [ < (S < (VP !$-- NP) !< SBAR) | < (VP !$-- NP) !< S ]",  // second disjunct matches errors where there is no S under SBAR and otherwise does no harm
            // matches subj in relative clauses
            "SBAR !< WHNP < (S !< (NP $++ VP)) > (VP > (S $- WHNP=target))",
            // matches subj in existential "there" SQ
            "SQ < ((NP < EX) $++ NP=target)",
            // matches subj in existential "there" S
            "S < (NP < EX) <+(VP) (VP < NP=target)",
            // matches (what, that) in "what is that" after the SQ has been flattened out of the tree
            "SBARQ < (/^(?:VB|AUX)/ < " + copularWordRegex + ") < (WHNP < WP) < NP=target",
            // matches (what, wrong) in "what is wrong with ..." after the SQ has been flattened out of the tree
            // note that in that case "wrong" is taken as the head thanks to UniversalSemanticHeadFinder hackery
            // The !$++ matches against (what, worth) in What is UAL stock worth?
            "SBARQ < (WHNP=target $++ ((/^(?:VB|AUX)/ < " + copularWordRegex + ") $++ ADJP=adj !$++ (NP $++ =adj)))",
            // the (NP < EX) matches (is, WHNP) in "what dignity is there in ..."
            // the PP matches (is, WHNP) in "what is on the test"
            "SBARQ <1 WHNP=target < (SQ < (/^(?:VB|AUX)/ < " + copularWordRegex + ") [< (NP < EX) | < PP])");


  /**
   * The "nominal passive subject" grammatical relation.  A nominal passive
   * subject is a subject of a passive which is an noun phrase.<p>
   * <p/>
   * Example: <br/>
   * "Dole was defeated by Clinton" &rarr; <code>nsubjpass</code>(defeated, Dole)
   * <p>
   * This pattern recognizes basic (non-coordinated) examples.  The coordinated
   * examples are currently handled by correctDependencies() in
   * EnglishGrammaticalStructure.  This seemed more accurate than any tregex
   * expression we could come up with.
   */
  public static final GrammaticalRelation NOMINAL_PASSIVE_SUBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "nsubjpass", "nominal passive subject",
        NOMINAL_SUBJECT, "S|SQ", tregexCompiler,
            "S|SQ < (WHNP|NP=target !< EX) < (VP < (/^(?:VB|AUX)/ < " + passiveAuxWordRegex + ")  < (VP < VBN|VBD))");


  /**
   * The "clausal subject" grammatical relation.  A clausal subject is
   * a subject which is a clause.<p>
   * <p/>
   * Examples: (subject is "what she said" in both examples) <br/>
   * "What she said makes sense" &rarr; <code>csubj</code>(makes, said) <br/>
   * "What she said is untrue" &rarr; <code>csubj</code>(untrue, said)
   */
  public static final GrammaticalRelation CLAUSAL_SUBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "csubj", "clausal subject",
        SUBJECT, "S", tregexCompiler,
            "S < (SBAR|S=target !$+ /^,$/ $++ (VP !$-- NP))");



  /**
   * The "clausal passive subject" grammatical relation.  A clausal passive subject is
   * a subject of a passive verb which is a clause.<p>
   * <p/>
   * Example: (subject is "that she lied") <br/>
   * "That she lied was suspected by everyone" &rarr; <code>csubjpass</code>(suspected, lied)
   */
  public static final GrammaticalRelation CLAUSAL_PASSIVE_SUBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "csubjpass", "clausal passive subject",
        CLAUSAL_SUBJECT, "S", tregexCompiler,
            "S < (SBAR|S=target !$+ /^,$/ $++ (VP < (VP < VBN|VBD) < (/^(?:VB|AUXG?)/ < " + passiveAuxWordRegex + ") !$-- NP))",
            "S < (SBAR|S=target !$+ /^,$/ $++ (VP <+(VP) (VP < VBN|VBD > (VP < (/^(?:VB|AUX)/ < " + passiveAuxWordRegex + "))) !$-- NP))");



  /**
   * The "complement" grammatical relation.  A complement of a VP
   * is any object (direct or indirect) of that VP, or a clause or
   * adjectival phrase which functions like an object; a complement
   * of a clause is an complement of the VP which is the predicate
   * of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "She gave me a raise" &rarr;
   * <code>comp</code>(gave, me),
   * <code>comp</code>(gave, a raise) <br/>
   * "I like to swim" &rarr;
   * <code>comp</code>(like, to swim)
   */
  public static final GrammaticalRelation COMPLEMENT =
    new GrammaticalRelation(Language.UniversalEnglish, "comp", "complement", ARGUMENT);


  /**
   * The "object" grammatical relation.  An object of a VP
   * is any direct object or indirect object of that VP; an object
   * of a clause is an object of the VP which is the predicate
   * of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "She gave me a raise" &rarr;
   * <code>obj</code>(gave, me),
   * <code>obj</code>(gave, raise)
   */
  public static final GrammaticalRelation OBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "obj", "object", COMPLEMENT);


  /**
   * The "direct object" grammatical relation.  The direct object
   * of a verb is the noun phrase which is the (accusative) object of
   * the verb; the direct object of a clause or VP is the direct object of
   * the head predicate of that clause.<p>
   * <p/>
   * Example: <br/>
   * "She gave me a raise" &rarr;
   * <code>dobj</code>(gave, raise) <p/>
   * Note that dobj can also be assigned by the conversion of rel in the postprocessing.
   */
  public static final GrammaticalRelation DIRECT_OBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "dobj", "direct object",
        OBJECT, "VP|SQ|SBARQ?", tregexCompiler,
            "VP !< (/^(?:VB|AUX)/ [ < " + copularWordRegex + " | < " + clausalComplementRegex + " ]) < (NP|WHNP=target [ [ !<# (/^NN/ < " + timeWordRegex + ") !$+ NP ] | $+ NP-TMP | $+ (NP <# (/^NN/ < " + timeWordRegex + ")) ] ) " +
                // The next qualification eliminates parentheticals that
                // come after the actual dobj
                " <# (__ !$++ (NP $++ (/^[:]$/ $++ =target))) ",

            // Examples such as "Rolls-Royce expects sales to remain steady"
            "VP < (S < (NP|WHNP=target $++ (VP < TO)))",

            // This matches rare cases of misparses, such as "What
            // disease causes cancer?" where the "causes" does not get a
            // surrounding VP.  Hopefully it does so without overlapping
            // any other dependencies.
            "SQ < (/^(?:VB)/=verb !< " + copularWordRegex + ") $-- WHNP !< VP !< (/^(?:VB)/ ! == =verb) < (NP|WHNP=target [ [ !<# (/^NN/ < " + timeWordRegex + ") !$+ NP ] | $+ NP-TMP | $+ (NP <# (/^NN/ < " + timeWordRegex + ")) ] )",

            // The rule for Wh-questions
            // cdm Jul 2010: No longer require WHNP as first child of SBARQ below: often not because of adverbials, quotes, etc., and removing restriction does no harm
            // this next pattern used to assume no empty NPs. Corrected.
            // One could require the VP at the end of the <+ to also be !< (/^(?:VB|AUX)/ $. SBAR) . This would be right for complement SBAR, but often avoids good matches for adverbial SBAR.  Adding it kills 4 good matches for avoiding 2 wrong matches on sum of TB3-train and EWT
            "SBARQ < (WHNP=target !< WRB !<# (/^NN/ < " + timeWordRegex + ")) <+(SQ|SINV|S|VP) (VP !< NP|TO !< (S < (VP < TO)) !< (/^(?:VB|AUX)/ < " + copularWordRegex + " $++ (VP < VBN|VBD)) !< (PP <: IN|TO) $-- (NP !< /^-NONE-$/))",

            // matches direct object in relative clauses with relative pronoun "I saw the book that you bought". Seems okay. If this is changed, also change the pattern for "rel"
            // TODO: this can occasionally produce incorrect dependencies, such as the sentence
            // "with the way which his split-fingered fastball is behaving"
            // eg take a tree where the verb doesn't have an object
            "SBAR < (WHNP=target !< WRB) < (S < NP < (VP !< SBAR !<+(VP) (PP <- IN|TO) !< (S < (VP < TO))))",

            // // matches direct object for long dependencies in relative clause without explicit relative pronouns
            // "SBAR !< (WHPP|WHNP|WHADVP) < (S < (@NP $++ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP)  !<+(VP) (/^(?:VB|AUX)/ < " + copularWordRegex + " $+ (VP < VBN|VBD)) !<+(VP) NP !< SBAR !<+(VP) (PP <- IN|TO)))) !$-- CC $-- NP > NP=target " +
            //   // avoid conflicts with rcmod.  TODO: we could look for
            //   // empty nodes in this kind of structure and use that to
            //   // find dobj, tmod, advmod, etc.  won't help the parser,
            //   // of course, but will help when converting a treebank
            //   // which contains empties
            //   // Example: "with the way his split-fingered fastball is behaving"
            //   "!($-- @NP|WHNP|NML > @NP|WHNP <: (S !< (VP < TO)))",

            // If there was an NP between the WHNP and the ADJP, we want
            // that NP to have the nsubj relation, and the WHNP is either
            // a dobj or a pobj instead.  For example, dobj(What, worth)
            // in "What is UAL stock worth?"
            "SBARQ < (WHNP=target $++ ((/^(?:VB|AUX)/ < " + copularWordRegex + ") $++ (ADJP=adj !< (PP !< NP)) $++ (NP $++ =adj)))"

            // Now allow $++ in main pattern above so don't need this.
            // "SBAR !< (WHPP|WHNP|WHADVP) < (S < (@NP $+ (ADVP $+ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP) !<+(VP) (/^(?:VB|AUX)/ < " + copularWordRegex + " $+ (VP < VBN|VBD)) !<+(VP) NP !< SBAR !<+(VP) (PP <- IN|TO))))) !$-- CC $-- NP > NP=target"

            // Excluding BE doesn't allow cases of NP-PRD followed by NP-TMP or NP-LOC like "These are Europeans next door."
            // Doc said: case with an iobj before dobj as two regular NPs. (This won't match if second one is explicitly NP-TMP.) But basic case covers this case. Does nothing.
            // "VP < (NP $+ (NP|WHNP=target !< (/^NN/ < " + timeWordLotRegex + "))) !<(/^(?:VB|AUX)/ < " + copularWordRegex + ")",  // this time one also included "lot"
            // Doc said: match "give it next week".  CDM 2013: I think this was put in to handle parse errors where the 2 NPs of a ditransitive were grouped into 1. But it is in principle wrong, and including it seems to be a no-op on TB3 WSJ. So exclude for now.
            // "VP < (NP < (NP $+ (/^(NP|WHNP)$/=target !< (/^NN/ < " + timeWordLotRegex + "))))!< (/^(?:VB|AUX)/ < " + copularWordRegex + ")",  // this time one also included "lot"

            // Doc said: matches direct object in relative clauses "I saw the book that you said you bought". But it didn't seem to determine anything.
            // This was various attempts at handling a long distance dependency, but that doesn't work; now handled through rel mechanism.
            // "SBAR !< WHNP|WHADVP < (S < (@NP $++ (VP !$++ NP))) > (VP > (S < NP $- WHNP=target))",
            // "SBAR !< WHNP|WHADVP|IN < (S < @NP < (VP !< (NP !<<# " + timeWordRegex + "))) > (VP > (S < NP $- WHNP=target))",
            // "S < (@NP !< /^-NONE-$/) <+(VP) (VP !< (@NP !< /^-NONE-$/ < (/^VB/ !< " + copularWordRegex + ")) !< CONJP|CC|SBAR) > (@SBAR !< @WHNP|WHADVP $- /^VB/ >+(VP|S|SBAR) (S < (@NP !< /^-NONE-$/ !<<# " + timeWordRegex + ") $- (@WHNP=target !< /^-NONE-$/ !<# WRB)))",

            // we now don't match "VBG > PP $+ NP=target", since it seems better to CM to regard these quasi preposition uses (like "including soya") as prepositions rather than verbs with objects -- that's certainly what the phrase structure at least suggests in the PTB.  They're now matched as pobj
    );


  /**
   * The "indirect object" grammatical relation.  The indirect
   * object of a VP is the noun phrase which is the (dative) object
   * of the verb; the indirect object of a clause is the indirect
   * object of the VP which is the predicate of that clause.
   * <p/>
   * Example:  <br/>
   * "She gave me a raise" &rarr;
   * <code>iobj</code>(gave, me)
   */
  public static final GrammaticalRelation INDIRECT_OBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "iobj", "indirect object",
        OBJECT, "VP", tregexCompiler,
            "VP < (NP=target !< /\\$/ !<# (/^NN/ < " + timeWordRegex + ") $+ (NP !<# (/^NN/ < " + timeWordRegex + ")))",
            // this next one was meant to fix common mistakes of our parser, but is perhaps too dangerous to keep
            // excluding selfRegex leaves out phrases such as "I cooked dinner myself"
            // excluding DT leaves out phrases such as "My dog ate it all""
            "VP < (NP=target < (NP !< /\\$/ $++ (NP !<: (PRP < " + selfRegex + ") !<: DT !< (/^NN/ < " + timeWordLotRegex + ")) !$ CC|CONJP !$ /^,$/ !$++ /^:$/))");

  //todo: integrate into something else for UD
  /**
   * The "prepositional complement" grammatical relation.
   * This is used when the complement of a preposition is a clause or
   * an adverbial or prepositional phrase.
   * The prepositional complement of
   * a preposition is the head of the sentence following the preposition,
   * or the preposition head of the PP.
   * <p/>
   * Examples: <br/>
   * "We have no useful information on whether users are at risk" &arr;
   * <code>pcomp</code>(on, are) <br/>
   * "They heard about you missing classes." &arr;
   * <code>pcomp</code>(about, missing) <br/>
   * It is warmer in Greece than in Italy &arr;
   * <code>pcomp</code>(than, in)
   */
 /* public static final GrammaticalRelation PREPOSITIONAL_COMPLEMENT =
    new GrammaticalRelation(Language.UniversalEnglish, "pcomp", "prepositional complement",
        COMPLEMENT, "(?:WH)?PP(?:-TMP)?", tregexCompiler,
            "@PP|WHPP < (IN|VBG|VBN|TO $+ @SBAR|S|PP|ADVP=target)", // no intervening NP; VBN is for "compared with"
            "@PP|WHPP < (RB $+ @SBAR|S=target)", // RB is for weird tagging like "after/RB adjusting for inflation"
            "@PP|WHPP !< IN|TO < (SBAR=target <, (IN $+ S))");
*/

  // /**
  //  * The "attributive" grammatical relation. The attributive is the complement of a
  //  * verb such as "to be, to seem, to appear".
  //  * <p>
  //  * These mainly occur in questions.  Arguably they shouldn't and we should treat the question
  //  * WHNP and WHADJP as predicates (as we do for ADJP and NP complements (NP-PRD and ADJP-PRD),
  //  * but we at present don't produce this.
  //  */
  // public static final GrammaticalRelation ATTRIBUTIVE =
  //   new GrammaticalRelation(Language.UniversalEnglish, "attr", "attributive",
  //       COMPLEMENT, "VP|SBARQ|SQ", tregexCompiler,
  //       new String[] {
  //         "VP < NP=target <(/^(?:VB|AUX)/ < " + copularWordRegex + ") !$ (NP < EX)",
  //         // "What is that?"
  //         "SBARQ < (WHNP|WHADJP=target $+ (SQ < (/^(?:VB|AUX)/ < " + copularWordRegex + " !$++ VP) !< (VP <- (PP <:IN)) !<- (PP <: IN)))",
  //         "SBARQ < (WHNP|WHADJP=target !< WRB) <+(SQ|SINV|S|VP) (VP !< (S < (VP < TO)) < (/^(?:VB|AUX)/ < " + copularWordRegex + " $++ (VP < VBN|VBD)) !<- PRT !<- (PP <: IN) $-- (NP !< /^-NONE-$/))",

  //         // "Is he the man?"
  //         "SQ <, (/^(?:VB|AUX)/ < " + copularWordRegex + ") < (NP=target $-- (NP !< EX))"
  //       });


  /**
   * The "clausal complement" grammatical relation.  A clausal complement of
   * a verb or adjective is a dependent clause with an internal subject which
   * functions like an object of the verb, or adjective.  Clausal complements
   * for nouns are limited to complement clauses with a subset of nouns
   * like "fact" or "report".  We analyze them the same (parallel to the
   * analysis of this class as "content clauses" in Huddleston and Pullum 2002).
   * Clausal complements are usually finite (though there
   * are occasional exceptions including remnant English subjunctives, and we
   * also classify the complement of causative "have" (She had him arrested)
   * in this category.<p>
   * <p/>
   * Example: <br/>
   * "He says that you like to swim" &rarr;
   * <code>ccomp</code>(says, like) <br/>
   * "I am certain that he did it" &rarr;
   * <code>ccomp</code>(certain, did) <br/>
   * "I admire the fact that you are honest" &rarr;
   * <code>ccomp</code>(fact, honest)
   */
  public static final GrammaticalRelation CLAUSAL_COMPLEMENT =
    new GrammaticalRelation(Language.UniversalEnglish, "ccomp", "clausal complement",
        COMPLEMENT, "VP|SINV|S|ADJP|ADVP|NP(?:-.*)?", tregexCompiler,
            // Weird case of verbs with direct S complement that is not an infinitive or participle
            // ("I saw [him take the cake].", "making [him go crazy]")
            "VP < (S=target < (VP !<, TO|VBG|VBN) !$-- NP)",
            // the canonical case of a SBAR[that] with an overt "that" or "whether"
            "VP < (SBAR=target < (S <+(S) VP) <, (IN|DT < /^(?i:that|whether)$/))",
            // Conjoined SBAR otherwise in the canonical case
            "VP < (SBAR=target < (SBAR < (S <+(S) VP) <, (IN|DT < /^(?i:that|whether)$/)) < CC|CONJP)",
            // This finds most ccomp SBAR[that] with omission of that, but only ones without dobj
            "VP < (SBAR=target < (S < VP) !$-- NP !<, (IN|WHADVP) !<2 (IN|WHADVP $- ADVP|RB))",
            // Find ccomp SBAR[that] after dobj for clear marker verbs
            "VP < (/^V/ < " + ccompObjVerbRegex + ") < (SBAR=target < (S < VP) $-- NP !<, (IN|WHADVP) !<2 (IN|WHADVP $- ADVP|RB))",
            "VP < (SBAR=target < (S < VP) !$-- NP <, (WHADVP < (WRB < /^(?i:how)$/)))",
            "VP < @SBARQ=target",  // Direct question: She asked "Who is in trouble"
            "VP < (/^VB/ < " + haveRegex + ") < (S=target < @NP < VP)",
            // !$-- @SBAR|S handles cases where the answer to the question
            //   "What do they ccompVerb?"
            //   is already answered by a different node
            // the ccompObjVerbRegex/NP test distinguishes "He told me why ..."
            //   vs "They know my order when ..."
            "VP < (@SBAR=target !$-- @SBAR|S !$-- /^:$/ [ == @SBAR=sbar | <# @SBAR=sbar ] ) < (/^V/ < " + ccompVerbRegex + ") [ < (/^V/ < " + ccompObjVerbRegex + ") | < (=target !$-- NP) ] : (=sbar < (WHADVP|WHNP < (WRB !< /^(?i:how)$/) !$-- /^(?!RB|ADVP).*$/) !< (S < (VP < TO)))",
            // to find "...", he said or "...?" he asked.
            // We eliminate conflicts with conj by looking for CC
            // Matching against "!< (VP < TO|VBG|VBN)" matches against vmod
            // "!< (VP <1 (VP [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ])))" also matches against vmod
            "@S|SINV < (@S|SBARQ=target $+ /^(,|\\.|'')$/ !$- /^(?:CC|CONJP|:)$/ !$- (/^(?:,)$/ $- CC|CONJP) !< (VP < TO|VBG|VBN) !< (VP <1 (VP [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ]))) !< (@S !== =target $++ =target !$++ @CC|CONJP)",
            // ADVP is things like "As long as they spend ..."
            // < WHNP captures phrases such as "no matter what", "no matter how", etc
            "ADVP < (SBAR=target [ < WHNP | ( < (IN < /^(?i:as|that)/) < (S < (VP !< TO))) ])",
            "ADJP < (SBAR=target !< (IN < as) < S)", // ADJP is things like "sure (that) he'll lose" or for/to ones or object of comparison with than "than we were led to expect"; Leave aside as in "as clever as we thought.
            // That ... he know
            "S <, (SBAR=target <, (IN < /^(?i:that|whether)$/) !$+ VP)",
            // JJ catches a couple of funny NPs with heads like "enough"
            // Note that we eliminate SBAR which also match an vmod pattern
            "@NP < JJ|NN|NNS < (SBAR=target [ !<(S < (VP < TO )) | !$-- NP|NN|NNP|NNS ] )");


  /**
   * An open clausal complement (<i>xcomp</i>) of a VP or an ADJP is a clausal
   * complement without its own subject, whose reference is determined by an
   * external subject.  These complements are always non-finite.
   * The name <i>xcomp</i> is borrowed from Lexical-Functional Grammar.
   * (Mainly "TO-clause" are recognized, but also some VBG like "stop eating")
   * <p/>
   * <p/>
   * Examples: <br/>
   * "I like to swim" &rarr;
   * <code>xcomp</code>(like, swim) <br/>
   * "I am ready to leave" &rarr;
   * <code>xcomp</code>(ready, leave)
   */
  public static final GrammaticalRelation XCLAUSAL_COMPLEMENT =
    new GrammaticalRelation(Language.UniversalEnglish, "xcomp", "xclausal complement",
        COMPLEMENT, "VP|ADJP|SINV", tregexCompiler,
            "VP < (S=target [ !$-- NP | $-- (/^V/ < " + xcompVerbRegex + ") ] !$- (NN < order) < (VP < TO))",    // used to have !> (VP < (VB|AUX < be))
            "ADJP < (S=target <, (VP <, TO))",
            "VP < (S=target !$- (NN < order) < (NP $+ NP|ADJP))",
            // to find "help sustain ...
            "VP <# (/^(?:VB|AUX)/ $+ (VP=target < VB|VBG))",
            "VP < (SBAR=target < (S !$- (NN < order) < (VP < TO))) !> (VP < (VB|AUX < be)) ",
            "VP < (S=target !$- (NN < order) <: NP) > VP",
            "VP < (/^VB/ $+ (@S=target < (@ADJP < /^JJ/ ! $-- @NP|S))) $-- (/^VB/ < " + copularWordRegex + " )",
            // stop eating
            // note that we eliminate parentheticals and clauses that could match a vmod
            // the clause !$-- VBG eliminates matches such as "What are you wearing dancing tonight"
            "(VP < (S=target < (VP < VBG ) !< NP !$- (/^,$/ [$- @NP|VP | $- (@PP $-- @NP ) |$- (@ADVP $-- @NP)]) !$-- /^:$/ !$-- VBG))",
            // Detects xcomp(becoming, requirement) in "Hand-holding is becoming an investment banking job requirement"
            // Also, xcomp(becoming, problem) in "Why is Dave becoming a problem?"
            "(VP $-- (/^(?:VB|AUX)/ < " + copularWordRegex + ") < (/^VB/ < " + clausalComplementRegex + ") < NP=target)",
            "VP < (/^(?:VB|AUX)/ < " + clausalComplementRegex + ") < (NP|WHNP=target [ [ !<# (/^NN/ < " + timeWordRegex + ") !$+ NP ] | $+ NP-TMP | $+ (NP <# (/^NN/ < " + timeWordRegex + ")) ] ) " +
                // The next qualification eliminates parentheticals that
                // come after the actual dobj
                " <# (__ !$++ (NP $++ (/^[:]$/ $++ =target))) ",
            // The old attr relation, used here to recover xcomp relations instead.
            "VP=vp < NP=target <(/^(?:VB|AUX)/ < " + copularWordRegex + " >># =vp) !$ (NP < EX)",
            // "Such a great idea this was" if "was" is the root, eg -makeCopulaHead
            "SINV <# (VP < (/^(?:VB|AUX)/ < " + copularWordRegex + ") $-- (NP $-- NP=target))",
            
            //Former acomp expression
            //TODO: potentially can be combined with existing expression
            "VP [ < ADJP=target | ( < (/^VB/ [ ( < " + clausalComplementRegex + " $++ VP=target ) | $+ (@S=target < (@ADJP < /^JJ/ ! $-- @NP|S)) ] ) !$-- (/^VB/ < " + copularWordRegex + " )) ]"
        );


  /**
   * The RELATIVE grammatical relation is only here as a temporary
   * relation.  This tregex triggering indicates either a dobj or a
   * pobj should be here.  We figure this out in a post-processing
   * step by looking at the surrounding dependencies.
   */
  public static final GrammaticalRelation RELATIVE =
    new GrammaticalRelation(Language.UniversalEnglish, "rel", "relative",
        COMPLEMENT, "SBAR", tregexCompiler,
            "SBAR < (WHNP=target !< WRB) < (S < NP < (VP [ < SBAR | <+(VP) (PP <- IN|TO) | < (S < (VP < TO)) ] ))");

  /**
   * The "referent" grammatical relation.  A
   * referent of the Wh-word of a NP is  the relative word introducing the relative clause modifying the NP.
   * <p/>
   * Example: <br/>
   * "I saw the book which you bought" &rarr;
   * <code>ref</code>(book, which) <br/>
   * "I saw the book the cover of which you designed" &rarr;
   * <code>ref</code>(book, which)
   */
  public static final GrammaticalRelation REFERENT =
    new GrammaticalRelation(Language.UniversalEnglish, "ref", "referent", DEPENDENT);



  /**
   * The "expletive" grammatical relation.
   * This relation captures an existential there.
   * <p/>
   * <p/>
   * Example: <br/>
   * "There is a statue in the corner" &rarr;
   * <code>expl</code>(is, there)
   */
  public static final GrammaticalRelation EXPLETIVE =
    new GrammaticalRelation(Language.UniversalEnglish, "expl", "expletive",
        DEPENDENT, "S|SQ|SINV", tregexCompiler,
            "S|SQ|SINV < (NP=target <+(NP) EX)");

 //TODO: integrate into xcomp
  /**
   * The "adjectival complement" grammatical relation.  An
   * adjectival complement of a VP is an adjectival phrase which
   * functions as the complement (like an object of the verb); an adjectival
   * complement of a clause is the adjectival complement of the VP which is
   * the predicate of that clause.<p>
   * <p/>
   * Example: <br/>
   * "She looks very beautiful" &rarr;
   * <code>acomp</code>(looks, beautiful)
   */
  /*

  public static final GrammaticalRelation ADJECTIVAL_COMPLEMENT =
    new GrammaticalRelation(Language.UniversalEnglish, "acomp", "adjectival complement",
        COMPLEMENT, "VP", tregexCompiler,
            "VP [ < ADJP=target | ( < (/^VB/ [ ( < " + clausalComplementRegex + " $++ VP=target ) | $+ (@S=target < (@ADJP < /^JJ/ ! $-- @NP|S)) ] ) !$-- (/^VB/ < " + copularWordRegex + " )) ]");


   */
  
  /**
   * The "modifier" grammatical relation.  A modifier of a VP is
   * any constituent that serves to modify the meaning of the VP
   * (but is not an <code>ARGUMENT</code> of that
   * VP); a modifier of a clause is an modifier of the VP which is
   * the predicate of that clause.<p>
   * <p/>
   * Examples: <br/>
   * "Last night, I swam in the pool" &rarr;
   * <code>mod</code>(swam, in the pool),
   * <code>mod</code>(swam, last night)
   */
  public static final GrammaticalRelation MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "mod", "modifier", DEPENDENT);


  /**
   * The "prepositional object" grammatical relation.  The object of a
   * preposition is the head of a noun phrase following the preposition, or
   * the adverbs "here" and "there".
   * (The preposition in turn may be modifying a noun, verb, etc.)
   * We here define cases of VBG quasi-prepositions like "including",
   * "concerning", etc. as instances of pobj (unlike the Penn Treebank).
   * <p/>
   * Example: <br/>
   * "I sat on the chair" &rarr;
   * <code>pobj</code>(on, chair)
   * <p/>
   * (The preposition can be called a FW for pace, versus, etc.  It can also
   * be called a CC - but we don't currently handle that and would need to
   * distinguish from conjoined PPs. Jan 2010 update: We now insist that the
   * NP must follow the preposition. This prevents a preceding NP measure
   * phrase being matched as a pobj.  We do allow a preposition tagged RB
   * followed by an NP pobj, as happens in the Penn Treebank for adverbial uses
   * of PP like "up 19%")
   */
  public static final GrammaticalRelation NOMINAL_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "nmod", "nominal modifier",
        MODIFIER, ".*", tregexCompiler,
            "/^(?:(?:WH)?(?:NP|ADJP|ADVP|NX|NML)(?:-TMP|-ADV)?|VP|NAC|SQ|FRAG|PRN|X|RRC)$/ < (WHPP|WHPP-TMP|PP|PP-TMP=target < @NP|WHNP|NML !$- (@CC|CONJP $- __)) !<- " + ETC_PAT + " !<- " + FW_ETC_PAT,
            "/^(?:(?:WH)?(?:NP|ADJP|ADVP|NX|NML)(?:-TMP|-ADV)?|VP|NAC|SQ|FRAG|PRN|X|RRC)$/ < (S=target <: WHPP|WHPP-TMP|PP|PP-TMP)",
            // only allow a PP < PP one if there is not a conj, verb, or other pattern that matches pcomp under it.  Else pcomp
            "WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV < (WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV=target !$- IN|VBG|VBN|TO) !< @CC|CONJP",
            "S|SINV < (PP|PP-TMP=target !< SBAR) < VP|S",
            "SBAR|SBARQ < /^(?:WH)?PP/=target < S|SQ",
            "@NP < (@UCP|PRN=target <# @PP)",
            // to handle "What weapon is Apollo most proficient with?"
            "SBARQ < (WHNP=target $++ ((/^(?:VB|AUX)/ < " + copularWordRegex + ") $++ (ADJP=adj < (PP !< NP)) $++ (NP $++ =adj)))");
  
            /*
            "/^(?:PP(?:-TMP)?|(?:WH)?(?:PP|ADVP))$/ < (SYM|IN|VBG|VBN|TO|FW|RB|RBR $++ (/^(?:WH)?(?:NP|ADJP)(?:-TMP|-ADV)?$/=target !$- @NP) !< /^(?i:not)$/)",
            // We allow ADVP with NP objects for cases like (ADVP earlier this year)
            "/^PP(?:-TMP)?$/ < (/^(?:IN|VBG|VBN|TO)$/ $+ (ADVP=target [ < (RB < /^(?i:here|there)$/) | < (ADVP < /^NP(?:-TMP)?$/) ] ))",
            // second disjunct is weird ADVP, only matches 1 tree in 2-21
            // to deal with preposition stranding in questions (e.g., "Which city do you live in?") -- the preposition is sometimes treated as a particle by the parser (works well but doesn't preserve the tree structure!)
            "PRT >- (VP !< (S < (VP < TO)) >+(SQ|SINV|S|VP) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/))",
            "(PP <: IN|TO) >- (VP !< (S < (VP < TO)) >+(SQ|SINV|S|VP) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/))",
            "(PP <: IN|TO) $- (NP $-- (VBZ|VBD) !$++ VP) >+(SQ) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/)",

            "XS|ADVP < (IN < /^(?i:at)$/) < JJS|DT=target", // at least, at most, at best, at worst, at all
            //"PP < (CC < less) < NP",
            "@PP < CC  < @NP=target !< @IN|TO|VBG|VBN|RB|RP|PP",  // for cases where "preposition" like "plus", "but", or "versus"
            // to handle "in and out of government"
            "@WHPP|PP < (@WHPP|PP $++ (CC|CONJP $++ (@WHPP|PP $+ (NP=target !$+ __))))",
            // to handle "What weapon is Apollo most proficient with?"
            "SBARQ < (WHNP=target $++ ((/^(?:VB|AUX)/ < " + copularWordRegex + ") $++ (ADJP=adj < (PP !< NP)) $++ (NP $++ =adj)))");
            */


  /**
   * The "prepositional object" grammatical relation.  The object of a
   * preposition is the head of a noun phrase following the preposition, or
   * the adverbs "here" and "there".
   * (The preposition in turn may be modifying a noun, verb, etc.)
   * We here define cases of VBG quasi-prepositions like "including",
   * "concerning", etc. as instances of pobj (unlike the Penn Treebank).
   * <p/>
   * Example: <br/>
   * "I sat on the chair" &rarr;
   * <code>pobj</code>(on, chair)
   * <p/>
   * (The preposition can be called a FW for pace, versus, etc.  It can also
   * be called a CC - but we don't currently handle that and would need to
   * distinguish from conjoined PPs. Jan 2010 update: We now insist that the
   * NP must follow the preposition. This prevents a preceding NP measure
   * phrase being matched as a pobj.  We do allow a preposition tagged RB
   * followed by an NP pobj, as happens in the Penn Treebank for adverbial uses
   * of PP like "up 19%")
   */
  
  /*
  public static final GrammaticalRelation PREPOSITIONAL_OBJECT =
    new GrammaticalRelation(Language.UniversalEnglish, "nmod2", "nominal modifier 2",
        MODIFIER, "SBARQ|PP(?:-TMP)?|WHPP|PRT|ADVP|WHADVP|XS", tregexCompiler,
            "/^(?:PP(?:-TMP)?|(?:WH)?(?:PP|ADVP))$/ < (SYM|IN|VBG|VBN|TO|FW|RB|RBR $++ (/^(?:WH)?(?:NP|ADJP)(?:-TMP|-ADV)?$/=target !$- @NP) !< /^(?i:not)$/)",
            // We allow ADVP with NP objects for cases like (ADVP earlier this year)
            "/^PP(?:-TMP)?$/ < (/^(?:IN|VBG|VBN|TO)$/ $+ (ADVP=target [ < (RB < /^(?i:here|there)$/) | < (ADVP < /^NP(?:-TMP)?$/) ] ))",
            // second disjunct is weird ADVP, only matches 1 tree in 2-21
            // to deal with preposition stranding in questions (e.g., "Which city do you live in?") -- the preposition is sometimes treated as a particle by the parser (works well but doesn't preserve the tree structure!)
            "PRT >- (VP !< (S < (VP < TO)) >+(SQ|SINV|S|VP) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/))",
            "(PP <: IN|TO) >- (VP !< (S < (VP < TO)) >+(SQ|SINV|S|VP) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/))",
            "(PP <: IN|TO) $- (NP $-- (VBZ|VBD) !$++ VP) >+(SQ) (SBARQ <, (WHNP=target !< WRB)) $-- (NP !< /^-NONE-$/)",

            "XS|ADVP < (IN < /^(?i:at)$/) < JJS|DT=target", // at least, at most, at best, at worst, at all
            //"PP < (CC < less) < NP",
            "@PP < CC  < @NP=target !< @IN|TO|VBG|VBN|RB|RP|PP",  // for cases where "preposition" like "plus", "but", or "versus"
            // to handle "in and out of government"
            "@WHPP|PP < (@WHPP|PP $++ (CC|CONJP $++ (@WHPP|PP $+ (NP=target !$+ __))))",
            // to handle "What weapon is Apollo most proficient with?"
            "SBARQ < (WHNP=target $++ ((/^(?:VB|AUX)/ < " + copularWordRegex + ") $++ (ADJP=adj < (PP !< NP)) $++ (NP $++ =adj)))");

*/

  /**
   * The "adverbial clause modifier" grammatical relation.  An adverbial clause
   * modifier of some predicates, such as a VP or (inverted) sentence is a clause modifying the verb
   * (temporal clauses, consequences, conditional clauses, etc.).
   * <p/>
   * Examples: <br/>
   * "The accident happened as the night was falling" &rarr;
   * <code>advcl</code>(happened, falling) <br/>
   * "If you know who did it, you should tell the teacher" &rarr;
   * <code>advcl</code>(tell, know)
   */
  public static final GrammaticalRelation ADV_CLAUSE_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "advcl", "adverbial clause modifier",
        MODIFIER, "VP|S|SQ|SINV|SBARQ|NP|ADVP|ADJP", tregexCompiler,
            "VP < (@SBAR=target <= (@SBAR [ < (IN|MWE !< /^(?i:that|whether)$/) | <: (SINV <1 /^(?:VB|MD|AUX)/) | < (RB|IN < so|now) < (IN < that) | <1 (ADVP < (RB < now)) <2 (IN < that) ] ))",
            "S|SQ|SINV < (SBAR|SBAR-TMP=target <, (IN|MWE !< /^(?i:that|whether)$/ !$+ (NN < order)) !$-- /^(?!CC|CONJP|``|,|INTJ|PP(-.*)?).*$/ !$+ VP)",
            // to get "rather than"
            //"S|SQ|SINV < (SBAR|SBAR-TMP=target <2 (IN|MWE !< /^(?i:that|whether)$/ !$+ (NN < order)) !$-- /^(?!CC|CONJP|``|,|INTJ|PP(-.*)?$).*$/)",
            // this one might just be better, but at any rate license one with quotation marks or a conjunction beforehand
            "S|SQ|SINV < (SBAR|SBAR-TMP=target <, (IN|MWE !< /^(?i:that|whether)$/ !$+ (NN < order)) !$+ @VP $+ /^,$/ $++ @NP)",
            // the last part should probably only be @SQ, but this captures some strays at no cost
            "SBARQ < (SBAR|SBAR-TMP|SBAR-ADV=target <, (IN|MWE !< /^(?i:that|whether)$/ !$+ (NN < order)) $+ /^,$/ $++ @SQ|S|SBARQ)",
            // added the (S < (VP <TO)) part so that "I tell them how to do so" doesn't get a wrong advcl
            // note that we allow adverb phrases to come before the WHADVP, which allows for phrases such as "even when"
            // ":" indicates something that should be a parataxis
            // in cases where there are two SBARs conjoined, we're happy
            // to use the head SBAR as a candidate for this relation
            "S|SQ < (@SBAR=target [ == @SBAR=sbar | <# @SBAR=sbar ] ): (=sbar < (WHADVP|WHNP < (WRB !< /^(?i:how)$/) !$-- /^(?!RB|ADVP).*$/) !< (S < (VP < TO)) !$-- /^:$/)",
            "VP < (@SBAR=target !$-- /^:$/ [ == @SBAR=sbar | <# @SBAR=sbar ] ) [ !< (/^V/ < " + ccompVerbRegex + ") | < (=target $-- @SBAR|S) | ( !< (/^V/ < " + ccompObjVerbRegex + ") < (=target $-- NP)) ] : (=sbar < (WHADVP|WHNP < (WRB !< /^(?i:how)$/) !$-- /^(?!RB|ADVP).*$/) !< (S < (VP < TO)))",
            // "S|SQ < (PP=target <, RB < @S)", // caught as prep and pcomp.
            "@S < (@SBAR=target $++ @NP $++ @VP)",  // fronted adverbial clause
            "@S < (@S=target < (VP < TO) $+ (/^,$/ $++ @NP))", // part of former purpcl: This is fronted infinitives: "To find out why, we went to ..."
            // "VP > (VP < (VB|AUX < be)) < (S=target !$- /^,$/ < (VP < TO|VBG) !$-- NP)", // part of former purpcl [cdm 2010: this pattern was added by me in 2006, but it is just bad!]

            // // matches direct object for long dependencies in relative clause without explicit relative pronouns
            // "SBAR !< (WHPP|WHNP|WHADVP) < (S < (@NP $++ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP)  !<+(VP) (/^(?:VB|AUX)/ < " + copularWordRegex + " $+ (VP < VBN|VBD)) !<+(VP) NP !< SBAR !<+(VP) (PP <- IN|TO)))) !$-- CC $-- NP > NP=target " +
            //   // avoid conflicts with rcmod.  TODO: we could look for
            //   // empty nodes in this kind of structure and use that to
            //   // find dobj, tmod, advmod, etc.  won't help the parser,
            //   // of course, but will help when converting a treebank
            //   // which contains empties
            //   // Example: "with the way his split-fingered fastball is behaving"
            //   "!($-- @NP|WHNP|NML > @NP|WHNP <: (S !< (VP < TO)))",
            "NP < (NP $++ (SBAR=target < (IN|MWE < /^(?i:than)$/) !< (WHPP|WHNP|WHADVP) < (S < (@NP $++ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP)  !<+(VP) (/^(?:VB|AUX)/ < " + copularWordRegex + " $+ (VP < VBN|VBD)) !<+(VP) NP !< SBAR !<+(VP) (PP <- IN|TO|MWE)))) !<: (S !< (VP < TO))) !$++ (CC $++ =target))",
            // this is for comparative or as ... as complements: sold more quickly [than they had expected]
            // available as long [as they install a crash barrier]
            "ADVP < ADVP < SBAR=target",
            
            //moved from vmod
            
            // to get "John, knowing ..., announced "
            // allowing both VP=verb and VP <1 VP=verb catches
            // conjunctions of two VP clauses
            "S|SINV < (S=target (< VP=verb | < (VP <1 VP=verb)) [ $- (/^,$/ [ $- @NP | $- (@PP $ @NP) ] ) | $+ (/^,$/ $+ @NP) ] ) : (=verb [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ])",
            "(VP < (@S=target < (VP [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ]) $- (/^,$/ [$- @NP|VP | $- (@PP $-- @NP ) |$- (@ADVP $-- @NP)])))",
            // What are you wearing dancing tonight?
            "(VP < (S=target < (VP < VBG) $-- VBG=ing !$-- (/^[:]$/ $-- =ing)))",
            // We could use something like this keying off -ADV annotation, but not yet operational, as we don't keep S-ADV, only NP-ADV
            // "VP < (/^S-ADV$/=target < (VP <, VBG|VBN) )",
            // they wrote asking the SEC to ...
            "VP < (S=target $-- NP < (VP < TO) !$-- (/^V/ < " + xcompVerbRegex + ") )",
            "SBARQ < WHNP < (S=target < (VP <1 TO))",
           
            //former pcomp
            "/^(?:(?:WH)?(?:ADJP|ADVP)(?:-TMP|-ADV)?|VP|SQ|FRAG|PRN|X|RRC)$/ < (WHPP|WHPP-TMP|PP|PP-TMP=target !< @NP|WHNP|NML !$- (@CC|CONJP $- __)) !<- " + ETC_PAT + " !<- " + FW_ETC_PAT);

//            "/^(?:(?:WH)?(?:NP|ADJP|ADVP|NX|NML)(?:-TMP|-ADV)?|VP|NAC|SQ|FRAG|PRN|X|RRC)$/ < (WHPP|WHPP-TMP|PP|PP-TMP=target < @NP|WHNP|NML !$- (@CC|CONJP $- __)) !<- " + ETC_PAT + " !<- " + FW_ETC_PAT);

  
  /*
   * The "purpose clause modifier" grammatical relation has been discontinued
   * It is now just seen as a special case of an advcl.  A purpose clause
   * modifier of a VP is a clause headed by "(in order) to" specifying a
   * purpose.  Note: at present we only recognize ones that have
   * "in order to" or are fronted.  Otherwise we can't use our surface representations to
   * distinguish these from xcomp's. We can also recognize "to" clauses
   * introduced by "be VBN".
   * <p/>
   * Example: <br/>
   * "He talked to the president in order to secure the account" &rarr;
   * <code>purpcl</code>(talked, secure)
   */


  /**
   * The "relative clause modifier" grammatical relation.  A relative clause
   * modifier of an NP is a relative clause modifying the NP.  The link
   * points from the head noun of the NP to the head of the relative clause,
   * normally a verb.
   * <p/>
   * <p/>
   * Examples: <br/>
   * "I saw the man you love" &rarr;
   * <code>relcl</code>(man, love)  <br/>
   * "I saw the book which you bought" &rarr;
   * <code>relcl</code>(book, bought)
   */
  public static final GrammaticalRelation RELATIVE_CLAUSE_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "acl:relcl", "relative clause modifier",
        MODIFIER, "(?:WH)?(?:NP|NML|ADVP)(?:-.*)?", tregexCompiler,
            "@NP|WHNP|NML=np $++ (SBAR=target [ <+(SBAR) WHPP|WHNP | <: (S !< (VP < TO)) ]) !$-- @NP|WHNP|NML !$++ " + ETC_PAT + " !$++ " + FW_ETC_PAT + " > @NP|WHNP : (=np !$++ (CC|CONJP $++ =target))",
            "NP|NML $++ (SBAR=target < (WHADVP < (WRB </^(?i:where|why|when)/))) !$-- NP|NML !$++ " + ETC_PAT + " !$++ " + FW_ETC_PAT + " > @NP",
            // for case of relative clauses with no relativizer
            // (it doesn't distinguish whether actually gapped).
            "@NP|WHNP < RRC=target <# NP|WHNP|NML|DT|S",
            "@ADVP < (@ADVP < (RB < /where$/)) < @SBAR=target",
            "NP < (NP $++ (SBAR=target !< (IN < /^(?i:than|that|whether)$/) !< (WHPP|WHNP|WHADVP) < (S < (@NP $++ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP)  !<+(VP) (/^(?:VB|AUX)/ < " + copularWordRegex + " $+ (VP < VBN|VBD)) !<+(VP) NP !< SBAR !<+(VP) (PP <- IN|TO)))) !<: (S !< (VP < TO))) !$++ (CC $++ =target))");


 /*
  * The "complementizer" grammatical relation is a discontinued grammatical relation. A
  * A complementizer of a clausal complement was the word introducing it.
  * It only matched "that" or "whether". We've now merged this in with "mark" which plays a similar
  * role with other clausal modifiers.
  * <p/>
  * <p/>
  * Example: <br/>
  * "He says that you like to swim" &rarr;
  * <code>complm</code>(like, that)
  */


  /**
   * The "marker" grammatical relation.  A marker is the word introducing a finite clause subordinate to another clause.
   * For a complement clause, this will typically be "that" or "whether".
   * For an adverbial clause, the marker is typically a preposition like "while" or "although".
   * <p/>
   * Example: <br/>
   * "U.S. forces have been engaged in intense fighting after insurgents launched simultaneous attacks" &rarr;
   * <code>mark</code>(launched, after)
   */
  public static final GrammaticalRelation MARKER =
    new GrammaticalRelation(Language.UniversalEnglish, "mark", "marker",
        MODIFIER, "SBAR(?:-TMP)?", tregexCompiler,
            "SBAR|SBAR-TMP < (IN|DT|MWE=target $++ S|FRAG)",
            "SBAR < (IN|DT=target < that|whether) [ $-- /^(?:VB|AUX)/ | $- NP|NN|NNS | > ADJP|PP | > (@NP|UCP|SBAR < CC|CONJP $-- /^(?:VB|AUX)/) ]");


  /**
   * The "adjectival modifier" grammatical relation.  An adjectival
   * modifier of an NP is any adjectival phrase that serves to modify
   * the meaning of the NP.<p>
   * <p/>
   * Example: <br/>
   * "Sam eats red meat" &rarr;
   * <code>amod</code>(meat, red) <p/>
   * The relation amod is also used for multiword country adjectives, despite their
   * questionable treebank representation.
   * <p/>
   * Example: <br/>
   * "the West German economy" &rarr;
   * <code>amod</code>(German, West),
   * <code>amod</code>(economy, German)
   */
  public static final GrammaticalRelation ADJECTIVAL_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "amod", "adjectival modifier",
        MODIFIER, "NP(?:-TMP|-ADV)?|NX|NML|NAC|WHNP|ADJP", tregexCompiler,
            "/^(?:NP(?:-TMP|-ADV)?|NX|NML|NAC|WHNP)$/ < (ADJP|WHADJP|JJ|JJR|JJS|JJP|VBN|VBG|VBD|IN=target !< (QP !< /^[$]$/) !$- CC)",
            // IN above is needed for "next" in "next week" etc., which is often tagged IN.
            "ADJP !< CC|CONJP < (JJ|NNP $ JJ|NNP=target)",
            // Cover the case of "John, 34, works at Stanford" - similar to an expression for appos
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (NP=target <: CD $- /^,$/ $-- /^(?:WH)?NP/ !$ CC|CONJP)");


  /**
   * The "numeric modifier" grammatical relation.  A numeric
   * modifier of an NP is any number phrase that serves to modify
   * the meaning of the NP.<p>
   * <p/>
   * Example: <br/>
   * "Sam eats 3 sheep" &rarr;
   * <code>num</code>(sheep, 3)
   */
  public static final GrammaticalRelation NUMERIC_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "nummod", "numeric modifier",
        MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?|NML|NX|ADJP|WHADJP|QP", tregexCompiler,
            "/^(?:WH)?(?:NP|NX|NML)(?:-TMP|-ADV)?$/ < (CD|QP=target !$- CC)",
            // $ is so phrases such as "$ 100 million buyout" get amod(buyout, $)
            "/^(?:WH)?(?:NP|NX|NML)(?:-TMP|-ADV)?$/ < (ADJP=target <: (QP !< /^[$]$/))",
            // Phrases such as $ 100 million get converted from (QP ($ $) (CD 100) (CD million)) to
            // (QP ($ $) (QP (CD 100) (CD million))).  This next tregex covers those phrases.
            // Note that the earlier tregexes are usually enough to cover those phrases, such as when
            // the QP is by itself in an ADJP or NP, but sometimes it can have other siblings such
            // as in the phrase "$ 100 million or more".  In that case, this next expression is needed.
            "QP < QP=target < /^[$]$/");


  //TODO: replace with compound
  /**
   * The "compound number modifier" grammatical relation.  A compound number
   * modifier is a part of a number phrase or currency amount.
   * <p/>
   * Example: <br/>
   * "I lost $ 3.2 billion" &rarr;
   * <code>number</code>($, billion)
   */
 /*
  public static final GrammaticalRelation NUMBER_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "number", "compound number modifier",
        MODIFIER, "QP|ADJP", tregexCompiler,
            "QP|ADJP < (/^(?:CD|$|#)$/=target !$- CC)");

*/
  /**
   * The "quantifier phrase modifier" grammatical relation.  A quantifier
   * modifier is an element modifying the head of a QP constituent.
   * <p/>
   * Example: <br/>
   * "About 200 people came to the party" &rarr;
   * <code>quantmod</code>(200, About)
   */
  /*
  public static final GrammaticalRelation QUANTIFIER_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "quantmod", "quantifier modifier",
        MODIFIER, "QP", tregexCompiler,
            "QP < IN|RB|RBR|RBS|PDT|DT|JJ|JJR|JJS|XS=target");
   */

  //TODO: update documentation
  //TODO: include numbers
  //TODO: exclude names
  /**
   * The "noun compound modifier" grammatical relation.  A noun compound
   * modifier of an NP is any noun that serves to modify the head noun.
   * Note that this has all nouns modify the rightmost a la Penn headship
   * rules.  There is no intelligent noun compound analysis.
   * <p/>
   * We eliminate nouns that are detected as part of a POS, since that
   * will turn into the dependencies denoting possession instead.
   * Note we have to include (VBZ &lt; /^\'s$/) as part of the POS
   * elimination, since quite a lot of text such as
   * "yesterday's widely published sequester" was misannotated as a
   * VBZ instead of a POS.  TODO: remove that if a revised PTB is ever
   * released.
   * <p/>
   * Example: <br/>
   * "Oil price futures" &rarr;
   * <code>compound</code>(futures, oil),
   * <code>compound</code>(futures, price) <p/>
   */
  public static final GrammaticalRelation NOUN_COMPOUND_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "compound", "compound modifier",
        MODIFIER, "(?:WH)?(?:NP|NX|NAC|NML|ADVP|ADJP|QP)(?:-TMP|-ADV)?", tregexCompiler,
            "/^(?:WH)?(?:NP|NX|NAC|NML)(?:-TMP|-ADV)?$/ < (NP|NML|NN|NNS|NNP|NNPS|FW|AFX=target $++ NN|NNS|NNP|NNPS|FW|CD=sister !<<- POS !<<- (VBZ < /^\'s$/) !$- /^,$/ !$++ (POS $++ =sister))",
            "/^(?:WH)?(?:NP|NX|NAC|NML)(?:-TMP|-ADV)?$/ < JJ|JJR|JJS=sister < (NP|NML|NN|NNS|NNP|NNPS|FW=target !<<- POS !<<- (VBZ < /^\'s$/) $+ =sister) <# NN|NNS|NNP|NNPS !<<- POS !<<- (VBZ < /^\'s$/) ",
            "QP|ADJP < (/^(?:CD|$|#)$/=target !$- CC)", //number relation in original SD
            // in vitro, in vivo, etc., in Genia
            // matches against "etc etc"
            "ADJP|ADVP < (FW [ $- (FW=target !< /^(?i:etc)$/) | $- (IN=target < in|In) ] )");

  /*
   * There used to be a relation "abbrev" for when abbreviations were defined in brackets after a noun
   * phrase, like "the Australian Broadcasting Corporation (ABC)", but it has now been disbanded, and
   * subsumed under appos.
   */

  /**
   * The "appositional modifier" grammatical relation.  An appositional
   * modifier of an NP is an NP that serves to modify
   * the meaning of the NP.  It includes parenthesized examples, as well as defining abbreviations.
   * <p/>
   * Examples: <br/>
   * "Sam, my brother, eats red meat" &rarr;
   * <code>appos</code>(Sam, brother) <br/>
   * "Bill (John's cousin)" &rarr; <code>appos</code>(Bill, cousin).
   *
   * "The Australian Broadcasting Corporation (ABC)" &rarr;
   *  <code>appos</code>(Corporation, ABC)
   */
  public static final GrammaticalRelation APPOSITIONAL_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "appos", "appositional modifier",
        MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?", tregexCompiler,
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (NP=target !<: CD $- /^,$/ $-- /^(?:WH)?NP/) !< CC|CONJP !< " + FW_ETC_PAT + " !< " + ETC_PAT,
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (PRN=target < (NP < /^(?:NN|CD)/ $-- /^-LRB-$/ $+ /^-RRB-$/))",
            // NP-ADV is a npadvmod, NP-TMP is a tmod
            "@WHNP|NP < (NP=target !<: CD <, /^-LRB-$/ <` /^-RRB-$/ $-- /^(?:WH)?NP/ !$ CC|CONJP)",
            // TODO: next pattern with NNP doesn't work because leftmost NNP is deemed head in a
            // structure like (NP (NNP Norway) (, ,) (NNP Verdens_Gang) (, ,))
            "NP|NP-TMP|NP-ADV < (NNP $+ (/^,$/ $+ NNP=target)) !< CC|CONJP !< " + FW_ETC_PAT + " !< " + ETC_PAT,
            // find abbreviations
            // for biomedical English, the former NNP heuristic really doesn't work, because they use NN for all chemical entities
            // while not unfoolable, this version produces less false positives and more true positives.
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (PRN=target <, /^-LRB-$/ <- /^-RRB-$/ !<< /^(?:POS|(?:WP|PRP)\\$|[,$#]|CC|RB|CD)$/ <+(NP) (NNP|NN < /^(?:[A-Z]\\.?){2,}/) )",
            // Handles cases such as "(NP (Her daughter) Jordan)"
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (NP=target <: NNP $- (/^(?:WH)?NP/ !< POS)) !< CC|CONJP !< " + FW_ETC_PAT + " !< " + ETC_PAT);


  /**
   * The "discourse element" grammatical relation. This is used for interjections and
   * other discourse particles and elements (which are not clearly linked to the structure
   * of the sentence, except in an expressive way). We generally follow the
   * guidelines of what the Penn Treebanks count as an INTJ.  They
   * define this to include: interjections (oh, uh-huh, Welcome), fillers (um, ah),
   * and discourse markers (well, like, actually, but not: you know).
   * We also use it for emoticons.
   */
   public static final GrammaticalRelation DISCOURSE_ELEMENT =
    new GrammaticalRelation(Language.UniversalEnglish, "discourse", "discourse element",
        MODIFIER, ".*", tregexCompiler,
            "__ < (NFP=target [ < " + WESTERN_SMILEY + " | < " + ASIAN_SMILEY + " ] )",
            "__ [ < INTJ=target | < (PRN=target <1 /^(?:,|-LRB-)$/ <2 INTJ [ !<3 __ | <3 /^(?:,|-RRB-)$/ ] ) ]");


  public static final GrammaticalRelation CLAUSAL_MODIFIER = 
      new GrammaticalRelation(Language.UniversalEnglish, "acl", "clausal modifier of noun",
          MODIFIER, "WHNP|WHNP-TMP|WHNP-ADV|NP(?:-[A-Z]+)?|NML|NX", tregexCompiler,
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV|NML|NX < (VP=target < VBG|VBN|VBD $-- @NP|NML|NX)",  // also allow VBD since it quite often occurs in treebank errors and parse errors
          // to get "MBUSA, headquartered ..."
          // Allows an adverb to come before the participle
          "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV|NML|NX < (/^,$/ $+ (VP=target [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ]))",
          
          //former pcomp
          "/^(?:(?:WH)?(?:NP|NX|NML)(?:-TMP|-ADV)?)$/ < (WHPP|WHPP-TMP|PP|PP-TMP=target !< @NP|WHNP|NML !$- (@CC|CONJP $- __)) !<- " + ETC_PAT + " !<- " + FW_ETC_PAT,

          
          "/^NP(?:-[A-Z]+)?$/ < (S=target < (VP < TO) $-- NP|NN|NNP|NNS)",
          "/^NP(?:-[A-Z]+)?$/ < (SBAR=target < (S < (VP < TO)) $-- NP|NN|NNP|NNS)");
   
   
  /**
   * The "verb modifier" grammatical relation.  A verb
   * modifier of an NP, VP, or S is a S/VP[part] that serves to modify
   * the meaning of the NP or VP.
   * <p/>
   * Examples: <br/>
   * "truffles picked during the spring are tasty" &rarr;
   * <code>vmod</code>(truffles, picked) <br>
   * "Bill picked Fred for the team demonstrating his incompetence" &rarr;
   * <code>vmod</code>(picked, demonstrating) <br>
   * "points to establish are ..." &rarr;
   * <code>vmod</code>(points, establish) <br>
   * "who am i to judge" &rarr;
   * <code>vmod</code>(who, judge) <br>
   */
  /*
  public static final GrammaticalRelation VERBAL_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "vmod", "verb modifier",
        MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?|NML|NX|VP|S|SINV|SBARQ", tregexCompiler,
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV|NML|NX < (VP=target < VBG|VBN|VBD $-- @NP|NML|NX)",  // also allow VBD since it quite often occurs in treebank errors and parse errors
            //acl
            // to get "MBUSA, headquartered ..."
            // Allows an adverb to come before the participle
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV|NML|NX < (/^,$/ $+ (VP=target [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ]))",
            //acl
            // to get "John, knowing ..., announced "
            // allowing both VP=verb and VP <1 VP=verb catches
            // conjunctions of two VP clauses
            "S|SINV < (S=target (< VP=verb | < (VP <1 VP=verb)) [ $- (/^,$/ [ $- @NP | $- (@PP $ @NP) ] ) | $+ (/^,$/ $+ @NP) ] ) : (=verb [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ])",
            //advcl
            "(VP < (@S=target < (VP [ <1 VBG|VBN | <2 (VBG|VBN $-- ADVP) ]) $- (/^,$/ [$- @NP|VP | $- (@PP $-- @NP ) |$- (@ADVP $-- @NP)])))",
            //advcl
            // What are you wearing dancing tonight?
            "(VP < (S=target < (VP < VBG) $-- VBG=ing !$-- (/^[:]$/ $-- =ing)))",
            //advcl
            // We could use something like this keying off -ADV annotation, but not yet operational, as we don't keep S-ADV, only NP-ADV
            // "VP < (/^S-ADV$/=target < (VP <, VBG|VBN) )",
            // they wrote asking the SEC to ...
            "VP < (S=target $-- NP < (VP < TO) !$-- (/^V/ < " + xcompVerbRegex + ") )",
            //advcl
            "/^NP(?:-[A-Z]+)?$/ < (S=target < (VP < TO) $-- NP|NN|NNP|NNS)",
            //acl
            "/^NP(?:-[A-Z]+)?$/ < (SBAR=target < (S < (VP < TO)) $-- NP|NN|NNP|NNS)",
            //acl
            "SBARQ < WHNP < (S=target < (VP <1 TO))");
            //advcl
  */

  /**
   * The "adverbial modifier" grammatical relation.  An adverbial
   * modifier of a word is a (non-clausal) RB or ADVP that serves to modify
   * the meaning of the word.<p>
   * <p/>
   * Examples: <br/>
   * "genetically modified food" &rarr;
   * <code>advmod</code>(modified, genetically) <br/>
   * "less often" &rarr;
   * <code>advmod</code>(often, less)
   */
  public static final GrammaticalRelation ADVERBIAL_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "advmod", "adverbial modifier",
        MODIFIER,
        "VP|ADJP|WHADJP|ADVP|WHADVP|S|SBAR|SINV|SQ|SBARQ|XS|(?:WH)?(?:PP|NP)(?:-TMP|-ADV)?|RRC|CONJP|JJP|QP", tregexCompiler,
            "/^(?:VP|ADJP|JJP|WHADJP|SQ?|SBARQ?|SINV|XS|RRC|(?:WH)?NP(?:-TMP|-ADV)?)$/ < (RB|RBR|RBS|WRB|ADVP|WHADVP=target !< " + NOT_PAT + " !< " + ETC_PAT + ")",
            "QP < IN|RB|RBR|RBS|PDT|DT|JJ|JJR|JJS|XS=target", //quantmod relation in original SD
            // avoids adverb conjunctions matching as advmod; added JJ to catch How long
            // "!< no" so we can get neg instead for "no foo" when no is tagged as RB
            // we allow CC|CONJP as long as it is not between the target and the head
            // TODO: perhaps remove that last clause if we transform
            // more and more, less and less, etc.
            "ADVP|WHADVP < (RB|RBR|RBS|WRB|ADVP|WHADVP|JJ=target !< " + NOT_PAT + " !< /^(?i:no)$/ !< " + ETC_PAT + ") [ !< /^CC|CONJP$/ | ( <#__=head !< (/^CC|CONJP$/ [ ($++ =head $-- =target) | ($-- =head $++ =target) ])) ]",
            //this one gets "at least" advmod(at, least) or "fewer than" advmod(than, fewer)
            "SBAR < (WHNP=target < WRB)", "SBARQ <, WHADVP=target", "XS < JJ=target",
            // for PP, only ones before head, or after NP, since others afterwards are pcomp
            "/(?:WH)?PP(?:-TMP|-ADV)?$/ <# (__ $-- (RB|RBR|RBS|WRB|ADVP|WHADVP=target !< " + NOT_PAT + " !< " + ETC_PAT + "))",
            "/(?:WH)?PP(?:-TMP|-ADV)?$/ < @NP|WHNP < (RB|RBR|RBS|WRB|ADVP|WHADVP=target !< " + NOT_PAT + " !< " + ETC_PAT + ")",
            "CONJP < (RB=target !< " + NOT_PAT + " !< " + ETC_PAT + ")");


  /**
   * The "negation modifier" grammatical relation.  The negation modifier
   * is the relation between a negation word and the word it modifies.
   * <p/>
   * Examples: <br/>
   * "Bill is not a scientist" &rarr;
   * <code>neg</code>(scientist, not) <br/>
   * "Bill doesn't drive" &rarr;
   * <code>neg</code>(drive, n't)
   */
  public static final GrammaticalRelation NEGATION_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "neg", "negation modifier",
        ADVERBIAL_MODIFIER,
        "VP|ADJP|S|SBAR|SINV|SQ|NP(?:-TMP|-ADV)?|FRAG|CONJP|PP|NAC|NML|NX|ADVP|WHADVP", tregexCompiler,
            "/^(?:VP|NP(?:-TMP|-ADV)?|ADJP|SQ|S|FRAG|CONJP|PP)$/< (RB=target < " + NOT_PAT + ")",
            "VP|ADJP|S|SBAR|SINV|FRAG < (ADVP=target <# (RB < " + NOT_PAT + "))",
            "VP > SQ $-- (RB=target < " + NOT_PAT + ")",
            // the commented out parts were relevant for the "det",
            // but don't seem to matter for the "neg" relation
            "/^(?:NP(?:-TMP|-ADV)?|NAC|NML|NX|ADJP|ADVP)$/ < (DT|RB=target < /^(?i:no)$/ " + /* !$++ CC */ " $++ /^(?:N[MNXP]|CD|JJ|JJR|FW|ADJP|QP|RB|RBR|PRP(?![$])|PRN)/ " + /* =det !$++ (/^PRP[$]|POS/ $++ =det !$++ (/''/ $++ =det)) */ ")",
            // catches "no more", possibly others as well
            // !< CC|CONJP catches phrases such as "no more or less", which maybe should be preconj
            "ADVP|WHADVP < (RB|RBR|RBS|WRB|ADVP|WHADVP|JJ=target < /^(?i:no)$/) !< CC|CONJP");


  /**
   * The "noun phrase as adverbial modifier" grammatical relation.
   * This relation captures various places where something syntactically a noun
   * phrase is used as an adverbial modifier in a sentence.  These usages include:
   * <ul>
   * <li> A measure phrase, which is the relation between
   * the head of an ADJP/ADVP and the head of a measure-phrase modifying the ADJP/ADVP.
   * <p/>
   * Example: <br/>
   * "The director is 65 years old" &rarr;
   * <code>npadvmod</code>(old, years)
   * </li>
   * <li> Noun phrases giving extent inside a VP which are not objects
   * <p/>
   * Example: <br/>
   * "Shares eased a fraction" &rarr;
   * <code>npadvmod</code>(eased, fraction)
   * </li>
   * <li> Financial constructions involving an adverbial or PP-like NP, notably
   * the following construction where the NP means "per share"
   * <p/>
   * Example: <br/>
   * "IBM earned $ 5 a share" &rarr;
   * <code>npadvmod</code>($, share)
   * </li>
   * <li>Reflexives
   * <p/>
   * Example: <br/>
   * "The silence is itself significant" &rarr;
   * <code>npadvmod</code>(significant, itself)
   * </li>
   * <li>Certain other absolutive NP constructions.
   * <p/>
   * Example: <br/>
   * "90% of Australians like him, the most of any country" &rarr;
   * <code>npadvmod</code>(like, most)
   * </ul>
   * A temporal modifier (tmod) is a subclass of npadvmod which is distinguished
   * as a separate relation.
   */
  public static final GrammaticalRelation NP_ADVERBIAL_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "nmod:npmod", "noun phrase adverbial modifier",
        MODIFIER, "VP|(?:WH)?(?:NP|ADJP|ADVP|PP)(?:-TMP|-ADV)?", tregexCompiler,
            "@ADVP|ADJP|WHADJP|WHADVP|PP|WHPP <# (JJ|JJR|IN|RB|RBR !< notwithstanding $- (@NP=target !< NNP|NNPS))",
            // one word nouns like "cost efficient", "ice-free"
            "@ADJP < (NN=target $++ /^JJ/) !< CC|CONJP",
            "@NP|WHNP < /^NP-ADV/=target",
            // Mr. Bush himself ..., in a couple different parse
            // patterns.  Looking for CC|CONJP leaves out phrases such
            // as "he and myself"
            "@NP|WHNP [ < (NP=target <: (PRP < " + selfRegex + ")) | < (PRP=target < " + selfRegex + ") ] : (=target $-- NP|NN|NNS|NNP|NNPS|PRP=noun !$-- (/^,|CC|CONJP$/ $-- =noun))",
            // this next one is for weird financial listings: 4.7% three months
            "@NP <1 (@NP <<# /^%$/) <2 (@NP=target <<# days|month|months) !<3 __",
            "@VP < /^NP-ADV/=target");


  /**
   * The "temporal modifier" grammatical relation.  A temporal
   * modifier of a VP or an ADJP is any constituent that serves to modify the
   * meaning of the VP or the ADJP by specifying a time; a temporal modifier of a
   * clause is an temporal modifier of the VP which is the
   * predicate of that clause.<p>
   * <p/>
   * Example: <br/>
   * "Last night, I swam in the pool" &rarr;
   * {@code tmod}(swam, night)
   */
  public static final GrammaticalRelation TEMPORAL_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "nmod:tmod", "temporal modifier",
        NP_ADVERBIAL_MODIFIER, "VP|S|ADJP|PP|SBAR|SBARQ|NP|RRC", tregexCompiler,
            "VP|ADJP|RRC [ < NP-TMP=target | < (VP=target <# NP-TMP !$ /^,|CC|CONJP$/) | < (NP=target <# (/^NN/ < " + timeWordRegex + ") !$+ (/^JJ/ < old)) ]",
            // CDM Jan 2010: For constructions like "during the same period last year"
            // combining expressions into a single disjunction should improve speed a little
            "@PP < (IN|TO|VBG|FW $++ (@NP [ $+ NP-TMP=target | $+ (NP=target <# (/^NN/ < " + timeWordRegex + ")) ]))",
            "S < (NP-TMP=target $++ VP $ NP )",
            "S < (NP=target <# (/^NN/ < " + timeWordRegex + ") $++ (NP $++ VP))",
            // matches when relative clauses as temporal modifiers of verbs!
            "SBAR < (@WHADVP < (WRB < when)) < (S < (NP $+ (VP !< (/^(?:VB|AUX)/ < " + copularWordRegex + " !$+ VP) ))) !$-- CC $-- NP > NP=target",
            "SBARQ < (@WHNP=target <# (/^NN/ < " + timeWordRegex + ")) < (SQ < @NP)",
            "NP < NP-TMP=target");

 //TODO: update
  /**
   * The "multi-word expression" grammatical relation.
   * This covers various multi-word constructions for which it would
   * seem pointless or arbitrary to claim grammatical relations between words:
   * as well as, rather than, instead of, but also;
   * such as, because of, all but, in addition to ....
   * <p/>
   * Examples: <br/>
   * "dogs as well as cats" &rarr;
   * <code>mwe</code>(well, as)<br/>
   * <code>mwe</code>(well, as)<p/>
   * "fewer than 700 bottles" &rarr;
   * <code>mwe</code>(than, fewer)
   */
  public static final GrammaticalRelation MULTI_WORD_EXPRESSION =
    new GrammaticalRelation(Language.UniversalEnglish, "mwe", "multi-word expression",
        MODIFIER, "PP|XS|ADVP|CONJP|MWE", tregexCompiler,
            "MWE < (IN|TO|RB|NP|NN|JJ|VB|CC|VBZ|VBD|ADVP|PP=target)"
            
            /*
            "PP|XS|CONJP < (IN|TO < as|of|at|to|in) < (JJ|IN|JJR|JJS|NN|VBN=target < such|because|Because|least|instead|due|Due|addition|opposed)",
            "ADVP < (RB|IN < well) < (IN|RB|JJS=target < as)",
            // TODO: perhaps the phrase "all but" is more like "all" and should have that as the head
            "ADVP < (DT|RB < all) < (CC|RB=target < but)",
            "CONJP < (RB < rather|well|instead) < (RB|IN=target < as|than|of)",
            "CONJP < (IN < in) < (NN|TO=target < addition|to)",
            "CONJP < RB < TO=target < ( VB < mention)", //"to" of "not to mention"
            "CONJP < RB < TO < ( VB=target < mention)", //"mention" of "not to mention"
            "CONJP < (VBN < opposed) < IN < (TO|IN=target < to)", //"to" of "as opposed to"
            // todo: note inconsistent head finding for "rather than"!
            "XS < JJR|JJS=target" // more than, fewer than, well over -- maybe change some of these?
            */
    );

  /* mihai: this block needs to be uncommented to get the KBP 2010 system to work (due to the cached sentences using old code)
   * (Note: in 2011, the measure phrase relation was collapsed into the scope of npadvmod, rather than being separated out.)
   **
   * The "measure-phrase" grammatical relation. The measure-phrase is the relation between
   * the head of an ADJP/ADVP and the head of a measure-phrase modifying the ADJP/ADVP.
   * <p/>
   * Example: <br/>
   * "The director is 65 years old" &rarr;
   * <code>measure</code>(old, years)
   *
  public static final GrammaticalRelation MEASURE_PHRASE =
    new GrammaticalRelation(Language.UniversalEnglish, "measure", "measure-phrase",
        MODIFIER, "ADJP|ADVP", tregexCompiler,
        new String[] {
          "ADJP <- JJ <, (NP=target !< NNP)",
          "ADVP|ADJP <# (JJ|IN $- NP=target)"
        });
  */ // mihai: end block

  /**
   * The "determiner" grammatical relation.
   * <p> <p/>
   * Examples: <br/>
   * "The man is here" &rarr; <code>det</code>(man,the) <br/>
   * "Which man do you prefer?" &rarr; <code>det</code>(man,which) <br>
   * (The ADVP match is because sometimes "a little" or "every time" is tagged
   * as an AVDVP with POS tags straight under it.)
   */
  public static final GrammaticalRelation DETERMINER =
    new GrammaticalRelation(Language.UniversalEnglish, "det", "determiner",
        MODIFIER, "(?:WH)?NP(?:-TMP|-ADV)?|NAC|NML|NX|X|ADVP|ADJP", tregexCompiler,
            "/^(?:NP(?:-TMP|-ADV)?|NAC|NML|NX|X)$/ < (DT=target !< /^(?i:either|neither|both|no)$/ !$+ DT !$++ CC $++ /^(?:N[MNXP]|CD|JJ|FW|ADJP|QP|RB|PRP(?![$])|PRN)/=det !$++ (/^PRP[$]|POS/ $++ =det !$++ (/''/ $++ =det)))",
            "NP|NP-TMP|NP-ADV < (DT=target [ (< /^(?i:either|neither|both)$/ !$+ DT !$++ CC $++ /^(?:NN|NX|NML)/ !$++ (NP < CC)) | " +
                                            "(!< /^(?i:either|neither|both|no)$/ $++ CC $++ /^(?:NN|NX|NML)/) | " +
                                            "(!< /^(?i:no)$/ $++ (/^JJ/ !$+ /^NN/) !$++CC !$+ DT) ] )",
            // "NP|NP-TMP|NP-ADV < (RB=target $++ (/^PDT$/ $+ /^NN/))", // todo: This matches nothing. Was it meant to be a PDT rule for (NP almost/RB no/DT chairs/NNS)?
            "NP|NP-TMP|NP-ADV <<, PRP <- (NP|DT|RB=target <<- all|both|each)", // we all, them all; various structures
            "WHNP < (NP $-- (WHNP=target < WDT))",
            // testing against CC|CONJP avoids conflicts with preconj in
            // phrases such as "both foo and bar"
            // however, we allow WDT|WP to account for "what foo or bar" and "whatever foo or bar"
            "@WHNP|ADVP|ADJP < (/^(?:NP|NN|CD|RBS|JJ)/ $-- (DT|WDT|WP=target !< /^(?i:no)$/ [ ==WDT|WP | !$++ CC|CONJP ]))",
            "@NP < (/^(?:NP|NN|CD|RBS)/ $-- WDT|WP=target)");


  /**
   * The "predeterminer" grammatical relation.
   * <p> <p/>
   * Example: <br/>
   * "All the boys are here" &rarr; <code>predet</code>(boys,all)
   */
  public static final GrammaticalRelation PREDETERMINER =
    new GrammaticalRelation(Language.UniversalEnglish, "det:predet", "predeterminer",
        MODIFIER, "(?:WH)?(?:NP|NX|NAC|NML)(?:-TMP|-ADV)?", tregexCompiler,
            "/^(?:(?:WH)?NP(?:-TMP|-ADV)?|NX|NAC|NML)$/ < (PDT|DT=target $+ /^(?:DT|WP\\$|PRP\\$)$/ $++ /^(?:NN|NX|NML)/ !$++ CC)",
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < (PDT|DT=target $+ DT $++ (/^JJ/ !$+ /^NN/)) !$++ CC",
            "WHNP|WHNP-TMP|WHNP-ADV|NP|NP-TMP|NP-ADV < PDT=target <- DT");


  /**
   * The "preconjunct" grammatical relation.
   * <p/>
   * Example: <br/>
   * "Both the boys and the girls are here" &rarr; <code>preconj</code>(boys,both)
   */
  //TODO: web_tbk/data/reviews/penntree/122270.xml.tree:
  // "both of the work.."
  public static final GrammaticalRelation PRECONJUNCT =
    new GrammaticalRelation(Language.UniversalEnglish, "cc:preconj", "preconjunct",
        MODIFIER,
        "S|VP|ADJP|PP|ADVP|UCP(?:-TMP|-ADV)?|NX|NML|SBAR|NP(?:-TMP|-ADV)?", tregexCompiler,
            "NP|NP-TMP|NP-ADV|NX|NML < (PDT|CC|DT=target < /^(?i:either|neither|both)$/ $++ CC)",
            "NP|NP-TMP|NP-ADV|NX|NML < (CONJP=target < (RB < /^(?i:not)$/) < (RB|JJ < /^(?i:only|merely|just)$/) $++ CC|CONJP)",
            // This matches weird/wrong NP-internal preconjuncts where you get (NP PDT (NP NP CC NP)) or similar
            "NP|NP-TMP|NP-ADV|NX|NML < (PDT|CC|DT=target < /^(?i:either|neither|both)$/ ) < (NP < CC)",
            "/^S|VP|ADJP|PP|ADVP|UCP(?:-TMP|-ADV)?|NX|NML|SBAR$/ < (PDT|DT|CC=target < /^(?i:either|neither|both)$/ $++ CC)",
            "/^S|VP|ADJP|PP|ADVP|UCP(?:-TMP|-ADV)?|NX|NML|SBAR$/ < (CONJP=target < (RB < /^(?i:not)$/) < (RB|JJ < /^(?i:only|merely|just)$/) $++ CC|CONJP)");


  //TODO: change some of it to nmod, ?also change pronouns?
  /**
   * The "possession" grammatical relation between the possessum and the possessor.<p>
   * </p>
   * Examples: <br/>
   * "their offices" &rarr;
   * {@code poss}(offices, their)<br/>
   * "Bill 's clothes" &rarr;
   * {@code poss}(clothes, Bill)
   */
  public static final GrammaticalRelation POSSESSION_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "nmod:poss", "possession modifier",
        MODIFIER, "(?:WH)?(NP|ADJP|INTJ|PRN|NAC|NX|NML)(?:-.*)?", tregexCompiler,
            "/^(?:WH)?(?:NP|INTJ|ADJP|PRN|NAC|NX|NML)(?:-.*)?$/ < /^(?:WP\\$|PRP\\$)$/=target",
            // todo: possessive pronoun under ADJP needs more work for one case of (ADJP his or her own)
            // basic NP possessive: we want to allow little conjunctions in head noun (NP (NP ... POS) NN CC NN) but not falsely match when there are conjoined NPs.  See tests.
            
            "/^(?:WH)?(?:NP|NML)(?:-.*)?$/ [ < (WHNP|WHNML|NP|NML=target [ < POS | < (VBZ < /^'s$/) ] ) !< (CC|CONJP $++ WHNP|WHNML|NP|NML) |  < (WHNP|WHNML|NP|NML=target < (CC|CONJP $++ WHNP|WHNML|NP|NML) < (WHNP|WHNML|NP|NML [ < POS | < (VBZ < /^'s$/) ] )) ]",
            // handle a few too flat NPs
            // note that ' matches both ' and 's
            "/^(?:WH)?(?:NP|NML|NX)(?:-.*)?$/ < (/^NN|NP/=target $++ (POS=pos < /\'/ $++ /^NN/) !$++ (/^NN|NP/ $++ =pos))"
            );


  //TODO: integrate into case
  /*
   * The "possessive" grammatical relation.  This is the relation given to
   * 's (or ' with plurals).<p>
   * </p>
   * Example: <br/>
   * "John's book" &rarr;
   * <code>possessive</code>(John, 's)
   
  public static final GrammaticalRelation POSSESSIVE_MODIFIER =
    new GrammaticalRelation(Language.UniversalEnglish, "possessive", "possessive modifier",
        MODIFIER, "(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?", tregexCompiler,
            "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ < POS=target",
            "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ < (VBZ=target < /^'s$/)");


   */

  //todo: update documentation
  /**
   * The "prepositional modifier" grammatical relation.  A prepositional
   * modifier of a verb, adjective, or noun is any prepositional phrase that serves to modify
   * the meaning of the verb, adjective, or noun.
   * We also generate prep modifiers of PPs to account for treebank (PP PP PP) constructions
   * (from 1984 through 2002). <p>
   * <p/>
   * Examples: <br/>
   * "I saw a cat in a hat" &rarr;
   * <code>case</code>(hat, in) <br/>
   * "I saw a cat with a telescope" &rarr;
   * <code>case</code>(telescope, with) <br/>
   * "He is responsible for meals" &rarr;
   * <code>case</code>(meals, for)
   */
  public static final GrammaticalRelation CASE_MARKER =
    new GrammaticalRelation(Language.UniversalEnglish, "case", "case marker",
        MODIFIER, "(?:WH)?(?:PP.*|SBARQ|NP|NML)(?:-TMP|-ADV)?", tregexCompiler,
            "/(?:WH)?PP(?:-TMP)?/ !$- (@CC|CONJP $- __) < IN|TO|MWE=target",
            "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ < POS=target", //'s
            "/^(?:WH)?(?:NP|NML)(?:-TMP|-ADV)?$/ < (VBZ=target < /^'s$/)", //'s
            

            //TODO: integrate the following into nmod???
            //"/^(?:(?:WH)?(?:NP|ADJP|ADVP|NX|NML)(?:-TMP|-ADV)?|VP|NAC|SQ|FRAG|PRN|X|RRC)$/ < (S=target <: WHPP|WHPP-TMP|PP|PP-TMP)",
            // only allow a PP < PP one if there is not a conj, verb, or other pattern that matches pcomp under it.  Else pcomp
            //"WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV < (WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV=target !$- IN|VBG|VBN|TO) !< @CC|CONJP",
            //"S|SINV < (PP|PP-TMP=target !< SBAR) < VP|S",
            //"SBAR|SBARQ < /^(?:WH)?PP/=target < S|SQ",
            // to handle "What weapon is Apollo most proficient with?"
            //"SBARQ < (WHNP $++ ((/^(?:VB|AUX)/ < " + copularWordRegex + ") $++ (ADJP=adj < (PP=target !< NP)) $++ (NP $++ =adj)))",
 
            // to handle "Nothing but their scratches"
            "/(?:WH)?PP(?:-TMP)?/ <1 CC=target <2 NP");
            
            
  
            /*
            "/^(?:(?:WH)?(?:NP|ADJP|ADVP|NX|NML)(?:-TMP|-ADV)?|VP|NAC|SQ|FRAG|PRN|X|RRC)$/ < (WHPP|WHPP-TMP|PP|PP-TMP=target !$- (@CC|CONJP $- __)) !<- " + ETC_PAT + " !<- " + FW_ETC_PAT,
            "/^(?:(?:WH)?(?:NP|ADJP|ADVP|NX|NML)(?:-TMP|-ADV)?|VP|NAC|SQ|FRAG|PRN|X|RRC)$/ < (S=target <: WHPP|WHPP-TMP|PP|PP-TMP)",
            // only allow a PP < PP one if there is not a conj, verb, or other pattern that matches pcomp under it.  Else pcomp
            "WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV < (WHPP|WHPP-TMP|WHPP-ADV|PP|PP-TMP|PP-ADV=target !$- IN|VBG|VBN|TO) !< @CC|CONJP",
            "S|SINV < (PP|PP-TMP=target !< SBAR) < VP|S",
            "SBAR|SBARQ < /^(?:WH)?PP/=target < S|SQ",
            "@NP < (@UCP|PRN=target <# @PP)");
            */


  /**
   * The "phrasal verb particle" grammatical relation.  The "phrasal verb particle"
   * relation identifies phrasal verb.<p>
   * <p/>
   * Example: <br/>
   * "They shut down the station." &rarr;
   * <code>prt</code>(shut, down)
   */
  public static final GrammaticalRelation PHRASAL_VERB_PARTICLE =
    new GrammaticalRelation(Language.UniversalEnglish, "compound:prt", "phrasal verb particle",
        MODIFIER, "VP|ADJP", tregexCompiler,
            "VP < PRT=target",
            "ADJP < /^VB/ < RP=target");


  /**
   * The "parataxis" grammatical relation. Relation between the main verb of a sentence
   * and other sentential elements, such as a sentential parenthetical, a sentence after a ":" or a ";", when two
   * sentences are juxtaposed next to each other without any coordinator or subordinator, etc.
   * <p> <p/>
   * Examples: <br/>
   * "The guy, John said, left early in the morning." &rarr; <code>parataxis</code>(left,said) <br/>
   * "
   */
  public static final GrammaticalRelation PARATAXIS =
    new GrammaticalRelation(Language.UniversalEnglish, "parataxis", "parataxis",
        DEPENDENT, "S|VP", tregexCompiler,
            "VP < (PRN=target < S|SINV|SBAR)", // parenthetical
            "VP $ (PRN=target [ < S|SINV|SBAR | < VP < @NP ] )", // parenthetical
            // The next relation handles a colon between sentences
            // and similar punct such as --
            // Sometimes these are lists, especially in the case of ";",
            // so we don't trigger if there is a CC|CONJP that occurs
            // anywhere other than the first child
            // First child can occur in rare circumstances such as
            // "But even if he agrees -- which he won't -- etc etc"
            "S|VP < (/^:$/ $+ /^S/=target) !<, (__ $++ CC|CONJP)",
            // two juxtaposed sentences; common in web materials (but this also matches quite a few wsj things)
            "@S < (@S|SBARQ $++ @S|SBARQ=target !$++ @CC|CONJP)",
            "@S|VP < (/^:$/ $-- /^V/ $+ @NP=target) !< @CONJP|CC" // sometimes CC cases are right node raising, etc.
    );

  /**
   * The "goes with" grammatical relation.  This corresponds to use of the GW (goes with) part-of-speech tag
   * in the recent Penn Treebanks. It marks partial words that should be combined with some other word. <p>
   * <p/>
   * Example: <br/>
   * "They come here with out legal permission." &rarr;
   * <code>goeswith</code>(out, with)
   */
  public static final GrammaticalRelation GOES_WITH =
    new GrammaticalRelation(Language.UniversalEnglish, "goeswith", "goes with",
        MODIFIER, ".*", tregexCompiler,
            "__ < GW=target");


  /**
   * The "semantic dependent" grammatical relation has been
   * introduced as a supertype for the controlling subject relation.
   */
  public static final GrammaticalRelation SEMANTIC_DEPENDENT =
    new GrammaticalRelation(Language.UniversalEnglish, "sdep", "semantic dependent", DEPENDENT);


  /**
   * The "agent" grammatical relation. The agent of a passive VP
   * is the complement introduced by "by" and doing the action.<p>
   * <p/>
   * Example: <br/>
   * "The man has been killed by the police" &rarr;
   * {@code agent}(killed, police)
   */
  public static final GrammaticalRelation AGENT =
    new GrammaticalRelation(Language.UniversalEnglish, "agent", "agent", DEPENDENT);


  // TODO would be nice to have this set up automatically...
  /**
   * A list of GrammaticalRelation values.  New GrammaticalRelations must be
   * added to this list (until we make this an enum!).
   * The GR recognizers are tried in the order listed.  A taxonomic
   * relationship trumps an ordering relationship, but otherwise, the first
   * listed relation will appear in dependency output.  Known ordering
   * constraints where both match include:
   * <ul>
   * <li>NUMERIC_MODIFIER &lt; ADJECTIVAL_MODIFIER
   * </ul>
   */
  @SuppressWarnings({"RedundantArrayCreation"})
  private static final List<GrammaticalRelation> values =
    Generics.newArrayList(Arrays.asList(new GrammaticalRelation[] {
      GOVERNOR,
      DEPENDENT,
      PREDICATE,
      AUX_MODIFIER,
      AUX_PASSIVE_MODIFIER,
      COPULA,
      CONJUNCT,
      COORDINATION,
      PUNCTUATION,
      ARGUMENT,
      SUBJECT,
      NOMINAL_SUBJECT,
      NOMINAL_PASSIVE_SUBJECT,
      CLAUSAL_SUBJECT,
      CLAUSAL_PASSIVE_SUBJECT,
      COMPLEMENT,
      OBJECT,
      DIRECT_OBJECT,
      INDIRECT_OBJECT,
      NOMINAL_MODIFIER,
      CLAUSAL_COMPLEMENT,
      XCLAUSAL_COMPLEMENT,
      MARKER,
      RELATIVE,
      REFERENT,
      EXPLETIVE,
      MODIFIER,
      ADV_CLAUSE_MODIFIER,
      TEMPORAL_MODIFIER,
      RELATIVE_CLAUSE_MODIFIER,
      NUMERIC_MODIFIER,
      ADJECTIVAL_MODIFIER,
      NOUN_COMPOUND_MODIFIER,
      APPOSITIONAL_MODIFIER,
      CLAUSAL_MODIFIER,
      ADVERBIAL_MODIFIER,
      NEGATION_MODIFIER,
      MULTI_WORD_EXPRESSION,
      DETERMINER,
      PREDETERMINER,
      PRECONJUNCT,
      POSSESSION_MODIFIER,
      CASE_MARKER,
      PHRASAL_VERB_PARTICLE,
      SEMANTIC_DEPENDENT,
      AGENT,
      NP_ADVERBIAL_MODIFIER,
      PARATAXIS,
      DISCOURSE_ELEMENT,
      GOES_WITH,
    }));
  // Cache frequently used views of the values list
  private static final List<GrammaticalRelation> unmodifiableValues =
    Collections.unmodifiableList(values);
  private static final List<GrammaticalRelation> synchronizedValues =
    Collections.synchronizedList(values);
  private static final List<GrammaticalRelation> unmodifiableSynchronizedValues =
    Collections.unmodifiableList(values);
  public static final ReadWriteLock valuesLock = new ReentrantReadWriteLock();

  // Map from English GrammaticalRelation short names to their corresponding
  // GrammaticalRelation objects
  public static final Map<String, GrammaticalRelation> shortNameToGRel = new ConcurrentHashMap<String, GrammaticalRelation>();
  static {
    for (GrammaticalRelation gr : values()) {
      shortNameToGRel.put(gr.toString().toLowerCase(), gr);
    }
  }

  public static List<GrammaticalRelation> values() {
    return values(false);
  }

  public static List<GrammaticalRelation> values(boolean threadSafe) {
    return threadSafe? unmodifiableSynchronizedValues : unmodifiableValues;
  }

  public static Lock valuesLock() {
    return valuesLock.readLock();
  }

  /**
   * This method is meant to be called when you want to add a relation
   * to the values list in a thread-safe manner.  Currently, this method
   * is always used in preference to values.add() because we expect to
   * add new EnglishGrammaticalRelations very rarely, so the eased
   * concurrency seems to outweigh the fairly slight cost of thread-safe
   * access.
   * @param relation the relation to be added to the values list
   */
  private static void threadSafeAddRelation(GrammaticalRelation relation) {
    valuesLock.writeLock().lock();
    try { // try-finally structure taken from Javadoc code sample for ReentrantReadWriteLock
      synchronizedValues.add(relation);
      shortNameToGRel.put(relation.toString(), relation);
    } finally {
      valuesLock.writeLock().unlock();
    }
  }



  // the exhaustive list of conjunction relations
  private static final Map<String, GrammaticalRelation> conjs = Generics.newConcurrentHashMap();

  public static Collection<GrammaticalRelation> getConjs() {
    return conjs.values();
  }

  /**
   * The "conj" grammatical relation. Used to collapse conjunct relations.
   * They will be turned into conj_word, where "word" is a conjunction.
   *
   * @param conjunctionString The conjunction to make a GrammaticalRelation out of
   * @return A grammatical relation for this conjunction
   */
  public static GrammaticalRelation getConj(String conjunctionString) {
    GrammaticalRelation result = conjs.get(conjunctionString);
    if (result == null) {
      synchronized(conjs) {
        result = conjs.get(conjunctionString);
        if (result == null) {
          result = new GrammaticalRelation(Language.UniversalEnglish, "conj", "conj_collapsed", CONJUNCT, conjunctionString);
          conjs.put(conjunctionString, result);
          threadSafeAddRelation(result);
        }
      }
    }
    return result;
  }

  // the exhaustive list of preposition relations
  private static final Map<String, GrammaticalRelation> preps = Generics.newConcurrentHashMap();
  private static final Map<String, GrammaticalRelation> prepsC = Generics.newConcurrentHashMap();


  public static Collection<GrammaticalRelation> getPreps() {
    return preps.values();
  }

  public static Collection<GrammaticalRelation> getPrepsC() {
    return prepsC.values();
  }


  /**
   * The "prep" grammatical relation. Used to collapse prepositions.<p>
   * They will be turned into prep_word, where "word" is a preposition.
   *
   * @param prepositionString The preposition to make a GrammaticalRelation out of
   * @return A grammatical relation for this preposition
   */
  public static GrammaticalRelation getPrep(String prepositionString) {
    GrammaticalRelation result = preps.get(prepositionString);
    if (result == null) {
      synchronized(preps) {
        result = preps.get(prepositionString);
        if (result == null) {
          result = new GrammaticalRelation(Language.UniversalEnglish, "prep", "prep_collapsed", CASE_MARKER, prepositionString);
          preps.put(prepositionString, result);
          threadSafeAddRelation(result);
        }
      }
    }
    return result;
  }


  /**
   * The "prepc" grammatical relation. Used to collapse preposition
   * complements.<p>
   * They will be turned into prep_word, where "word" is a preposition.
   *
   * @param prepositionString The preposition to make a GrammaticalRelation out of
   * @return A grammatical relation for this preposition
   */
  public static GrammaticalRelation getPrepC(String prepositionString) {
    GrammaticalRelation result = prepsC.get(prepositionString);
    if (result == null) {
      synchronized(prepsC) {
        result = prepsC.get(prepositionString);
        if (result == null) {
          result = new GrammaticalRelation(Language.UniversalEnglish, "prepc", "prepc_collapsed", DEPENDENT, prepositionString);
          prepsC.put(prepositionString, result);
          threadSafeAddRelation(result);
        }
      }
    }
    return result;
  }


  /**
   * Returns the EnglishGrammaticalRelation having the given string
   * representation (e.g., "nsubj"), or null if no such is found.
   *
   * @param s The short name of the GrammaticalRelation
   * @return The EnglishGrammaticalRelation with that name
   */
  public static GrammaticalRelation valueOf(String s) {
    return GrammaticalRelation.valueOf(s, values);

//    // TODO does this need to be changed?
//    // modification NOTE: do not commit until go-ahead
//    // If this is a collapsed relation (indicated by a "_" separating
//    // the type and the dependent, instantiate a collapsed version.
//    // Currently handcode against conjunctions and prepositions, but
//    // should do this in a more robust fashion.
//    String[] tuples = s.trim().split("_", 2);
//    if (tuples.length == 2) {
//      String reln = tuples[0];
//      String specific = tuples[1];
//      if (reln.equals(PREPOSITIONAL_MODIFIER.getShortName())) {
//        return getPrep(specific);
//      } else if (reln.equals(CONJUNCT.getShortName())) {
//        return getConj(specific);
//      }
//    }
//
//    return null;
  }

  /**
   * Returns an EnglishGrammaticalRelation based on the argument.
   * It works if passed a GrammaticalRelation or the String
   * representation of one (e.g., "nsubj").  It returns {@code null}
   * for other classes or if no string match is found.
   *
   * @param o A GrammaticalRelation or String
   * @return The EnglishGrammaticalRelation with that name
   */
  @SuppressWarnings("unchecked")
  public static GrammaticalRelation valueOf(Object o) {
    if (o instanceof GrammaticalRelation) {
      return (GrammaticalRelation) o;
    } else if (o instanceof String) {
      return valueOf((String) o);
    } else {
      return null;
    }
  }

  /**
   * Prints out the English grammatical relations hierarchy.
   * See {@code EnglishGrammaticalStructure} for a main method that
   * will print the grammatical relations of a sentence or tree.
   *
   * @param args Args are ignored.
   */
  public static void main(String[] args) {
    System.out.println(DEPENDENT.toPrettyString());
  }

}

