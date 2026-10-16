package org.dariusturcu.backend.pipeline;

// Thrown when a worker reports on a job whose lease it no longer holds, for example after
// the lease expired and another worker took the job.
public class LeaseNotHeldException extends IllegalStateException {

    public LeaseNotHeldException(long jobId, String leaseOwner) {
        super("Job " + jobId + " is not leased by " + leaseOwner);
    }
}
