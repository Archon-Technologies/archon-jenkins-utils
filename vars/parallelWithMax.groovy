def call(Map args = [:]) {
 Map<String, Closure> jobs = args.jobs ?: [:]
 int maxNumber = args.maxNumber ?: 5

 def semaphore = new java.util.concurrent.Semaphore(maxNumber)

 def throttled = jobs.collectEntries { name, body ->
  [(name): {
   semaphore.acquire()
   try {
    return body()
   } finally {
    semaphore.release()
   }
  }]
 }

 parallel throttled
}
