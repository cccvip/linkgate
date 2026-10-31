package com.linkgate.bench;

/**
 * 压测命令行入口。
 *
 * <pre>
 * 用法：BenchMain [host] [port] [connections] [IDLE|ACTIVE] [seconds]
 * 例：  BenchMain 127.0.0.1 8080 10000 IDLE 60
 * </pre>
 */
public final class BenchMain {

    private BenchMain() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        int connections = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        BenchScenario scenario = args.length > 3 ? BenchScenario.valueOf(args[3].toUpperCase()) : BenchScenario.IDLE;
        long seconds = args.length > 4 ? Long.parseLong(args[4]) : 30;

        try (BenchClient client = new BenchClient(host, port)) {
            long start = System.currentTimeMillis();
            client.connect(connections);
            System.out.printf("connected %d/%d in %dms%n",
                    client.connectedCount(), connections, System.currentTimeMillis() - start);

            BenchResult result = client.run(scenario, seconds * 1000);
            System.out.println(result.report());
        }
    }
}
