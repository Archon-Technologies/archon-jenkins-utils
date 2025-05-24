def call(args = [:]) {
 def jobs = args.jobs ?: [:]
 int maxConcurrent = args.maxConcurrent ?: 5

 // never spin up more threads than jobs
 int maxWorkers = Math.min(maxConcurrent, jobs.size())

 // thread-safe queue of all work items
 def jobQueue = new java.util.concurrent.LinkedBlockingQueue(jobs.entrySet())
 // thread-safe map of results
 def results  = new java.util.concurrent.ConcurrentHashMap()

 // build our N workers
 def workers = [:]
 def errorToThrow = null
 for (int i = 1; i <= maxWorkers; i++) {
  workers["worker-${i}"] = {
   while (true) {
    // non-blocking pull
    def entry = jobQueue.poll()
    if (entry == null) {
     break
    }
    try {
     def (name, body) = [entry.key, entry.value]
     def result = body.call()
     if (result) {
      results.put(name, result)
     }
    } catch (e) {
     echo "Error in ${name}: ${e}"
     // store the error to throw later
     errorToThrow = e
    }
   }
  }
 }

 // fire them off in parallel
 parallel workers

 if (errorToThrow) {
  // throw the error after all workers have completed
  throw errorToThrow
 }

 return results
}
