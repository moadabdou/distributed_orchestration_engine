
from fernos import DAG , SleepJob, JobGroup, FernOSClient



with DAG("complex_sleep_workflow") as dag:
    
    
    tasks : list[SleepJob] = [SleepJob(ms=5000, label=f"task{i}") for i in range(10)]


    JobGroup([tasks[0], tasks[1]]) >> tasks[2] >> JobGroup([tasks[3], tasks[4]]) >> tasks[5] >> JobGroup([tasks[6], tasks[7]]) >> tasks[8] >> tasks[9]


if __name__ == "__main__":


    client = FernOSClient()

    workflow = client.register_dag(dag)

    print(f"workflows status {workflow.status}")

    workflow.wait_for_completion(5*60*1000)

    print(f"workflows status {workflow.status}")






        

