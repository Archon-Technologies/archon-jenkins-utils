def call(Map args = [:]) {
 Map<String, Closure> jobs = args.jobs ?: [:]
 int maxNumber = args.maxConcurrent ?: 5
 // Build a thread‐safe queue of the remaining work
 def jobQueue = new java.util.concurrent.LinkedBlockingQueue<Map.Entry<String,Closure>>(jobs.entrySet())

 // A helper that pulls one job off the queue and wraps it
 def scheduleOne = {
  def entry = jobQueue.poll()
  if (entry == null) {
   return null
  }
  [(entry.key): {
      try {
        entry.value.call()
      } finally {
        // When this branch completes, schedule exactly one more
        def next = scheduleOne()
        if (next) {
          parallel next
        }
      }
    }]
 }

 if (maxNumber < jobs.size()) {
  // Don't bother spinning up more threads than we have jobs
  maxNumber = jobs.size()
 }

 // Kick off up to maxNumber initial branches
 def initial = [:]
 for (int i = 0; i < maxNumber; i++) {
  def one = scheduleOne()
  if (one) {
   initial.putAll(one)
        } else {
   break
  }
 }

 if (initial) {
  parallel initial
 }
}
