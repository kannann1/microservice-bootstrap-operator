package io.github.kannann1.microservicebootstrapoperator.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Utility class for handling retries with exponential backoff
 */
@Slf4j
public class RetryUtil {

    /**
     * Retry a callable with exponential backoff
     *
     * @param callable The callable to retry
     * @param maxRetries Maximum number of retries
     * @param initialBackoffMs Initial backoff in milliseconds
     * @param maxBackoffMs Maximum backoff in milliseconds
     * @param <T> The return type of the callable
     * @return The result of the callable
     * @throws Exception If the callable fails after all retries
     */
    public static <T> T retryWithExponentialBackoff(
            Callable<T> callable,
            int maxRetries,
            long initialBackoffMs,
            long maxBackoffMs) throws Exception {
        return retryWithExponentialBackoff(callable, maxRetries, initialBackoffMs, maxBackoffMs, e -> true);
    }

    /**
     * Retry a callable with exponential backoff if the exception matches the retryPredicate
     *
     * @param callable The callable to retry
     * @param maxRetries Maximum number of retries
     * @param initialBackoffMs Initial backoff in milliseconds
     * @param maxBackoffMs Maximum backoff in milliseconds
     * @param retryPredicate Predicate to determine if the exception should trigger a retry
     * @param <T> The return type of the callable
     * @return The result of the callable
     * @throws Exception If the callable fails after all retries
     */
    public static <T> T retryWithExponentialBackoff(
            Callable<T> callable,
            int maxRetries,
            long initialBackoffMs,
            long maxBackoffMs,
            Predicate<Exception> retryPredicate) throws Exception {
        
        int retries = 0;
        long backoffMs = initialBackoffMs;
        Exception lastException = null;

        while (retries <= maxRetries) {
            try {
                return callable.call();
            } catch (Exception e) {
                lastException = e;
                
                if (!retryPredicate.test(e)) {
                    log.warn("Exception does not match retry criteria, failing immediately", e);
                    throw e;
                }
                
                if (retries == maxRetries) {
                    log.error("Failed after {} retries", maxRetries, e);
                    throw e;
                }
                
                log.warn("Attempt {} failed, retrying in {} ms", retries + 1, backoffMs, e);
                
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                // Exponential backoff with jitter
                backoffMs = Math.min(maxBackoffMs, (long) (backoffMs * (1.5 + Math.random() * 0.5)));
                retries++;
            }
        }
        
        // This should never happen due to the throw in the loop
        throw new RuntimeException("Unexpected error in retry logic", lastException);
    }
    
    /**
     * Execute a runnable with retries
     *
     * @param runnable The runnable to execute
     * @param maxRetries Maximum number of retries
     * @param initialBackoffMs Initial backoff in milliseconds
     * @param maxBackoffMs Maximum backoff in milliseconds
     * @throws Exception If the runnable fails after all retries
     */
    public static void executeWithRetry(
            ThrowingRunnable runnable,
            int maxRetries,
            long initialBackoffMs,
            long maxBackoffMs) throws Exception {
        
        retryWithExponentialBackoff(() -> {
            runnable.run();
            return null;
        }, maxRetries, initialBackoffMs, maxBackoffMs);
    }
    
    /**
     * Functional interface for a runnable that can throw exceptions
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
