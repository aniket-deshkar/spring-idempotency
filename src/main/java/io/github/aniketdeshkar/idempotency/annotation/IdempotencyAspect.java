package io.github.aniketdeshkar.idempotency.annotation;

import io.github.aniketdeshkar.idempotency.IdempotencyException;
import io.github.aniketdeshkar.idempotency.IdempotencyExecution;
import io.github.aniketdeshkar.idempotency.IdempotencyExecutor;
import io.github.aniketdeshkar.idempotency.RequestFingerprinter;
import io.github.aniketdeshkar.idempotency.StoredResponse;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import tools.jackson.databind.ObjectMapper;

@Aspect
public final class IdempotencyAspect {
  private final IdempotencyExecutor executor;
  private final ObjectMapper objectMapper;
  private final Duration defaultTimeToLive;
  private final ExpressionParser expressionParser = new SpelExpressionParser();
  private final DefaultParameterNameDiscoverer parameterNames =
      new DefaultParameterNameDiscoverer();

  public IdempotencyAspect(
      IdempotencyExecutor executor, ObjectMapper objectMapper, Duration defaultTimeToLive) {
    this.executor = Objects.requireNonNull(executor, "executor");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.defaultTimeToLive = Objects.requireNonNull(defaultTimeToLive, "defaultTimeToLive");
  }

  @Around("@annotation(idempotent)")
  public Object invoke(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    EvaluationContext context = evaluationContext(method, joinPoint.getArgs());
    String key = evaluate(idempotent.key(), context);
    String fingerprintSource =
        idempotent.fingerprint().isBlank()
            ? serialize(joinPoint.getArgs())
            : evaluate(idempotent.fingerprint(), context);
    Duration ttl =
        idempotent.ttlSeconds() > 0
            ? Duration.ofSeconds(idempotent.ttlSeconds())
            : defaultTimeToLive;
    IdempotencyExecution execution =
        executor.execute(
            method.getDeclaringClass().getName() + ":" + method.getName() + ":" + key,
            RequestFingerprinter.sha256(fingerprintSource),
            ttl,
            () -> {
              Object value;
              try {
                value = joinPoint.proceed();
              } catch (Exception exception) {
                throw exception;
              } catch (Throwable throwable) {
                throw new IdempotencyException("Idempotent invocation failed", throwable);
              }
              return new StoredResponse(
                  200, "application/json", Map.of(), objectMapper.writeValueAsBytes(value));
            });
    return objectMapper.readValue(
        execution.response().body(),
        objectMapper.getTypeFactory().constructType(method.getGenericReturnType()));
  }

  private EvaluationContext evaluationContext(Method method, Object[] arguments) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    String[] names = parameterNames.getParameterNames(method);
    for (int index = 0; names != null && index < names.length; index++) {
      context.setVariable(names[index], arguments[index]);
      context.setVariable("p" + index, arguments[index]);
    }
    return context;
  }

  private String evaluate(String expression, EvaluationContext context) {
    return Objects.requireNonNull(
        expressionParser.parseExpression(expression).getValue(context, String.class),
        "expression result");
  }

  private String serialize(Object value) {
    return objectMapper.writeValueAsString(value);
  }
}
