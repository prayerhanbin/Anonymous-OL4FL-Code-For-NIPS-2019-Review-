import communication.mserver.MParaChannel;
import communication.mserver.MServer;
import communication.utils.Para;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SyncFixedCost {

    public static void main(String[] args) throws IOException{

        // Cluster number
        int k =6;

        // Batch size
        int miniBatchNum =500;

        KMeans readDataKMeans = new KMeans();
        readDataKMeans.readData();

        MServer server = new MServer(8803);
        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                server.begin();
            }
        }).start();

        List<ArrayList<Double>> globalcenter = new ArrayList<>();
        ArrayList<Double> DBI = new ArrayList<Double>();
        ArrayList<Double> f1=new ArrayList<Double>();
        List<ArrayList<Double>> oldcenter = new ArrayList<>();
        double[] distance = new double[k];
        HashMap<Integer, Integer> armMap = new HashMap<>();
        armMap.put(0, 51);
        armMap.put(1, 40);
        armMap.put(2, 27);
        armMap.put(3, 18);
        armMap.put(4, 6);
        armMap.put(5, 3);
        FixedCost fixedCost = new FixedCost();

        boolean isFirst = true;
        int recycleCount = 0;
        int clientNum = 3;
        int N = 100;
        int sum = 0;

        List<MParaChannel> paraList = new ArrayList<>();

        double[] slavedbi=new double[clientNum];
        long[] sendtime=new long[clientNum];
        long[] uploadtime=new long[clientNum];
        long[] runtime=new long[clientNum];

        // Connect 3 slaves, and then initialize global parameter
        while (clientNum != MServer.paraQueue.size()) {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        for (int j = 0; j < clientNum; j++) {
            MParaChannel paraChannel = MServer.paraQueue.poll();
            if (null != paraChannel) {
                paraList.add(paraChannel);
                //....
            }
        }
        System.out.println("wait for 3 slave in paraQueue");
        paraList.clear();

        Para paraMtoS = new Para();
        paraMtoS.time = 1;
        paraMtoS.centerList = globalcenter;
        MServer.serverHandler.broadcast(paraMtoS);

        for( int i = 0; i < N; i++ ) {
            while (clientNum != MServer.paraQueue.size()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            recycleCount = i;

            for (int j = 0; j < clientNum; j++) {
                MParaChannel paraChannel = MServer.paraQueue.poll();
                if (null != paraChannel) {
                    paraList.add(paraChannel);
                    //....
                }
            }

            long receiveTime = System.currentTimeMillis();

            // Receive local parameter and update global parameter
            KMeans kmeans = new KMeans(
                    paraList.get(0).paraStoM.centerList, paraList.get(1).paraStoM.centerList, paraList.get(2).paraStoM.centerList,
                    paraList.get(0).paraStoM.num, paraList.get(1).paraStoM.num, paraList.get(2).paraStoM.num, k);

            for (int j=0;j<clientNum;j++) {
                slavedbi[j] = paraList.get(j).paraStoM.DBI;
            }
            paraList.clear();
            globalcenter = kmeans.getCenter();

            if(!oldcenter.isEmpty()) {
                for (int t = 0; t < k; t++) {
                    distance[t]=0;
                    for (int j = 0; j < globalcenter.get(0).size(); j++) {
                        distance[t] += (globalcenter.get(t).get(j) - oldcenter.get(t).get(j)) * (globalcenter.get(t).get(j) - oldcenter.get(t).get(j));
                    }
                    distance[t]=Math.sqrt(distance[t]);
                    System.out.println("distance"+t+":" + distance[t] );
                }

            }
            for(int j = 0; j < globalcenter.size(); j++){
                oldcenter.add( (ArrayList<Double>)globalcenter.get(j).clone());
            }

            // Get DBI
            KMeans kmean=new KMeans(k, globalcenter,miniBatchNum);
            List<ArrayList<Double>> test_center = kmean.getNewCenter();
            DBI test=new DBI(test_center, kmean.getHelpCenterList());
            DBI.add(test.dbi);

            // Get F1-score
            F1score f1Score =new F1score(kmean.train_target,kmean.predict_target);
            f1.add(f1Score.f1);


            if( isFirst ){
                isFirst = false;
            } else {
                fixedCost.updateEstimate();
            }

            // The distribution of each arm of MAB is modified according to the value of F1,
            // so as to select the arm I of the next iteration
            int arm = -1;
            if( fixedCost.isResourceEnough() ){
                arm = fixedCost.mab(1);
            }
            else {
                System.out.println("run out of resource");
                break;
            }

            int t = armMap.get(arm);

            // Master send parameter to slave
            paraMtoS.time = t;
            paraMtoS.centerList = globalcenter;
            MServer.serverHandler.broadcast(paraMtoS);

        }
        System.out.println("ready to stop!!!");
        paraMtoS = new Para();
        paraMtoS.state = -1;
        MServer.serverHandler.broadcast(paraMtoS);
        MServer.closeGracefully();

        System.out.println("END");
        if( N ==  (recycleCount+1) ){
            System.out.println("Normal Exit !");

        }else{
            System.out.println("Abnormal Exit：expect" + N + "times，but only executed" + (recycleCount+1) + "times");
        }
        HashMap<String, List<Double>> mapRegret = new HashMap<>();
        mapRegret.put("sync", fixedCost.regrets);
//        System.out.println("\n\nDBI: " + DBI);
        System.out.println("\n\nF1: " + f1);


    }
}
