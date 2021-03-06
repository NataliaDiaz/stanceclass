package edu.ucsc.cs.workshop

import java.util.Set;
import edu.umd.cs.bachuai13.util.DataOutputter;
import edu.umd.cs.bachuai13.util.FoldUtils;
import edu.umd.cs.bachuai13.util.GroundingWrapper;
import edu.umd.cs.psl.application.inference.MPEInference
import edu.umd.cs.psl.application.inference.LazyMPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxPseudoLikelihood
import edu.umd.cs.psl.application.learning.weight.maxmargin.MaxMargin
import edu.umd.cs.psl.application.learning.weight.maxmargin.MaxMargin.NormScalingType
import edu.umd.cs.psl.application.learning.weight.random.FirstOrderMetropolisRandOM
import edu.umd.cs.psl.application.learning.weight.random.HardEMRandOM
import edu.umd.cs.psl.application.learning.weight.em.HardEM
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.core.*
import edu.umd.cs.psl.core.inference.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database
import edu.umd.cs.psl.database.DatabasePopulator
import edu.umd.cs.psl.database.DatabaseQuery
import edu.umd.cs.psl.database.Partition
import edu.umd.cs.psl.database.ResultList
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.evaluation.result.*
import edu.umd.cs.psl.evaluation.statistics.DiscretePredictionComparator
import edu.umd.cs.psl.evaluation.statistics.DiscretePredictionStatistics
import edu.umd.cs.psl.evaluation.statistics.filter.MaxValueFilter
import edu.umd.cs.psl.groovy.*
import edu.umd.cs.psl.model.Model
import edu.umd.cs.psl.model.argument.ArgumentType
import edu.umd.cs.psl.model.argument.GroundTerm
import edu.umd.cs.psl.model.argument.UniqueID
import edu.umd.cs.psl.model.argument.Variable
import edu.umd.cs.psl.model.atom.GroundAtom
import edu.umd.cs.psl.model.atom.QueryAtom
import edu.umd.cs.psl.model.atom.RandomVariableAtom
import edu.umd.cs.psl.model.kernel.CompatibilityKernel
import edu.umd.cs.psl.model.parameters.PositiveWeight
import edu.umd.cs.psl.model.parameters.Weight
import edu.umd.cs.psl.ui.loading.*
import edu.umd.cs.psl.util.database.Queries

import edu.umd.cs.psl.evaluation.statistics.RankingScore
import edu.umd.cs.psl.evaluation.statistics.SimpleRankingComparator


//dataSet = "fourforums"
dataSet = "stance-classification"
ConfigManager cm = ConfigManager.getManager()
ConfigBundle cb = cm.getBundle(dataSet)

def defaultPath = System.getProperty("java.io.tmpdir")
//String dbPath = cb.getString("dbPath", defaultPath + File.separator + "psl-" + dataSet)
String dbPath = cb.getString("dbPath", defaultPath + File.separator + dataSet)
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, true), cb)

fold = args[1]
def dir = 'data'+java.io.File.separator + fold + java.io.File.separator + 'train' + java.io.File.separator;
def testdir = 'data'+java.io.File.separator + fold + java.io.File.separator + 'test' + java.io.File.separator;

PSLModel model = new PSLModel(this, data)

initialWeight = 1

/* 
 * List of predicates with their argument types
 * writesPost(Author, Post) -- observed
 * participatesIn(Author, Topic) -- observed
 * hasTopic(Post, Topic) -- observed
 * isProAuth(Author, Topic) -- target
 * isProPost(Post, Topic) -- target
 * agreesAuth(Author, Author) -- observed 
 * agreesPost(Post, Post) -- observed
 * hasLabelPro(Post, Topic) -- observed
 */

model.add predicate: "writesPost" , types:[ArgumentType.UniqueID, ArgumentType.UniqueID]
model.add predicate: "participates" , types:[ArgumentType.UniqueID, ArgumentType.String]
model.add predicate: "hasTopic" , types:[ArgumentType.UniqueID, ArgumentType.String]
model.add predicate: "isProAuth" , types:[ArgumentType.UniqueID, ArgumentType.String]
model.add predicate: "isProPost" , types:[ArgumentType.UniqueID, ArgumentType.String]
model.add predicate: "agreesAuth" , types:[ArgumentType.UniqueID, ArgumentType.UniqueID, ArgumentType.String]
model.add predicate: "disagreesAuth" , types:[ArgumentType.UniqueID, ArgumentType.UniqueID, ArgumentType.String]
model.add predicate: "hasLabelPro" , types:[ArgumentType.UniqueID, ArgumentType.String]
model.add predicate: "topic" , types:[ArgumentType.String]

/*
 * Rule expressing that an author and their post will have the same stances and same agreement behavior 
 * Note that the second is logically equivalent to saying that if author is pro then post will be pro - contrapositive
 */

//model.add rule : (isProPost(P, T) & writesPost(A, P)) >> isProAuth(A, T), weight : 1
//model.add rule : (~(isProPost(P, T)) & writesPost(A, P) & hasTopic(P, T))>> ~(isProAuth(A, T)), weight : 1


model.add rule : (isProPost(P, T) & writesPost(A, P)) >> isProAuth(A, T), weight : 1
model.add rule : (isProAuth(A, T) & writesPost(A, P) & hasTopic(P, T)) >> isProPost(P, T), weight :1
model.add rule : (~isProPost(P, T) & writesPost(A, P) & hasTopic(P,T)) >> ~isProAuth(A, T), weight : 1
model.add rule : (~isProAuth(A, T) & writesPost(A, P) & hasTopic(P, T)) >> ~isProPost(P, T), weight : 1


/*
 * Rules for relating stance with agreement/disagreement
 */

/*
model.add rule : (agreesPost(P1, P2) & (P1^P2) & isProPost(P1, T)) >> isProPost(P2, T), weight : 1
model.add rule : (agreesPost(P1, P2) & (P1^P2) & ~(isProPost(P1, T))) >> ~(isProPost(P2, T)), weight : 1
model.add rule : (~(agreesPost(P1, P2)) & (P1^P2) & isProPost(P1, T)) >> ~(isProPost(P2, T)), weight : 1
model.add rule : (~(agreesPost(P1, P2)) & (P1^P2) & ~(isProPost(P1, T))) >> isProPost(P2, T), weight : 1
*/


model.add rule : (agreesAuth(A1, A2, T) & (A1^A2) & participates(A2, T) & isProAuth(A1, T)) >> isProAuth(A2, T), weight : 1
model.add rule : (agreesAuth(A1, A2, T) & (A1^A2) & participates(A1, T) & ~(isProAuth(A1, T))) >> ~(isProAuth(A2, T)), weight : 1
model.add rule : (disagreesAuth(A1, A2, T) & (A1^A2) & participates(A2, T) & isProAuth(A1, T)) >> ~(isProAuth(A2, T)), weight : 1
model.add rule : (disagreesAuth(A1, A2, T) & (A1^A2) & topic(T) & participates(A1, T) & ~(isProAuth(A1, T))) >> isProAuth(A2, T), weight : 1


//Prior that the label given by the text classifier is indeed the stance label

model.add rule : (hasLabelPro(P, T)) >> isProPost(P, T) , weight : 1
model.add rule : (~(hasLabelPro(P, T))) >> ~(isProPost(P, T)) , weight : 1

/*
 * Inserting data into the data store
 */

Partition observed_tr = new Partition(0);
Partition predict_tr = new Partition(1);
Partition truth_tr = new Partition(2);
Partition observed_te = new Partition(3);
Partition predict_te = new Partition(4);
Partition truth_te = new Partition(5);
Partition dummy_tr = new Partition(6);
Partition dummy_te = new Partition(7);

inserter = data.getInserter(hasLabelPro, observed_tr)
InserterUtils.loadDelimitedDataTruth(inserter, dir+"hasLabelPro.csv", ",");

inserter = data.getInserter(hasTopic, observed_tr)
InserterUtils.loadDelimitedData(inserter, dir+"hasTopic.csv", ",");

inserter = data.getInserter(writesPost, observed_tr)
InserterUtils.loadDelimitedData(inserter, dir+"writesPost.csv", ",");

inserter = data.getInserter(topic, observed_tr)
InserterUtils.loadDelimitedData(inserter, dir+"topics.csv", ",");

inserter = data.getInserter(agreesAuth, observed_tr)
InserterUtils.loadDelimitedData(inserter, dir+"agreesAuth.csv",",");

inserter = data.getInserter(disagreesAuth, observed_tr)
InserterUtils.loadDelimitedData(inserter, dir+"disagreesAuth.csv", ",");

inserter = data.getInserter(participates, observed_tr)
InserterUtils.loadDelimitedData(inserter, dir+"participates.csv", ",");


/*
 * Ground truth for training data for weight learning
 */

inserter = data.getInserter(isProPost, truth_tr)
InserterUtils.loadDelimitedDataTruth(inserter, dir+"isProPost.csv",",");

inserter = data.getInserter(isProAuth, truth_tr)
InserterUtils.loadDelimitedDataTruth(inserter, dir+"isProAuth.csv", ",");

/*
 * Testing split for model inference
 * Observed partitions
 */

inserter = data.getInserter(hasLabelPro, observed_te)
InserterUtils.loadDelimitedDataTruth(inserter, testdir+"hasLabelPro.csv", ",");

inserter = data.getInserter(hasTopic, observed_te)
InserterUtils.loadDelimitedData(inserter, testdir+"hasTopic.csv", ",");

inserter = data.getInserter(writesPost, observed_te)
InserterUtils.loadDelimitedData(inserter, testdir+"writesPost.csv",",");

inserter = data.getInserter(topic, observed_te)
InserterUtils.loadDelimitedData(inserter, testdir+"topics.csv",",");

inserter = data.getInserter(agreesAuth, observed_te)
InserterUtils.loadDelimitedData(inserter, testdir+"agreesAuth.csv",",");

inserter = data.getInserter(disagreesAuth, observed_te)
InserterUtils.loadDelimitedData(inserter, testdir+"disagreesAuth.csv", ",");

inserter = data.getInserter(participates, observed_te)
InserterUtils.loadDelimitedData(inserter, testdir+"participates.csv",",")

/*
 * Label partitions
 */

inserter = data.getInserter(isProPost, truth_te)
InserterUtils.loadDelimitedDataTruth(inserter, testdir+"isProPost.csv",",");

inserter = data.getInserter(isProAuth, truth_te)
InserterUtils.loadDelimitedDataTruth(inserter, testdir+"isProAuth.csv", ",");

/*
 * Set up training databases for weight learning
 */

Database distributionDB = data.getDatabase(predict_tr, [participates, agreesAuth, disagreesAuth, hasLabelPro, hasTopic, writesPost, topic] as Set, observed_tr);
Database truthDB = data.getDatabase(truth_tr, [isProPost, isProAuth] as Set)


/* Populate isProPost in observed DB. */
DatabasePopulator dbPop = new DatabasePopulator(distributionDB);
dbPop.populateFromDB(truthDB, isProPost);


/* Populate isProAuth in observed DB. */
DatabasePopulator populator = new DatabasePopulator(distributionDB);
populator.populateFromDB(truthDB, isProAuth);


MaxLikelihoodMPE weightLearning = new MaxLikelihoodMPE(model, distributionDB, truthDB, cb);
//println "about to start weight learning"
weightLearning.learn();
//println " finished weight learning "
weightLearning.close();

/*
 MaxPseudoLikelihood mple = new MaxPseudoLikelihood(model, trainDB, truthDB, cb);
 println "about to start weight learning"
 mple.learn();
 println " finished weight learning "
 mlpe.close();
 */

//println model;

Database testDB = data.getDatabase(predict_te, [participates, agreesAuth, disagreesAuth, hasLabelPro, hasTopic, writesPost, topic] as Set, observed_te);
Database testTruthDB = data.getDatabase(truth_te, [isProPost, isProAuth] as Set)

/* Populate isProPost in test DB. */

DatabasePopulator test_pop = new DatabasePopulator(testDB);
test_pop.populateFromDB(testTruthDB, isProPost);


/* Populate isProAuth in test DB. */

DatabasePopulator test_populator = new DatabasePopulator(testDB);
test_populator.populateFromDB(testTruthDB, isProAuth);

/*
 * Inference
 */

MPEInference mpe = new MPEInference(model, testDB, cb)
FullInferenceResult result = mpe.mpeInference()
//System.out.println("Objective: " + result.getTotalWeightedIncompatibility())

/* Evaluation */

def comparator = new SimpleRankingComparator(testDB)
comparator.setBaseline(testTruthDB)

// Choosing what metrics to report
def metrics = [RankingScore.AUPRC, RankingScore.NegAUPRC,  RankingScore.AreaROC]
double [] score = new double[metrics.size()]

try {
    for (int i = 0; i < metrics.size(); i++) {
            comparator.setRankingScore(metrics.get(i))
            score[i] = comparator.compare(isProPost)
    }
    //Storing the performance values of the current fold
    
    System.out.println(fold + "," + score[0] + "," + score[1] + "," + score[2])
    

//    System.out.println("\nArea under positive-class PR curve: " + score[0])
//    System.out.println("Area under negative-class PR curve: " + score[1])
//    System.out.println("Area under ROC curve: " + score[2])
}
catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("No evaluation data! Terminating!");
}

testTruthDB.close()
testDB.close()
distributionDB.close()
truthDB.close()
