package gj.cloud.ops.application.preview.flow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowExpressionTest {

    @Test
    void parsesAllFiveScopesWithDotPaths() {
        assertThat(FlowExpression.parse("$form.name")).contains(
                new FlowExpression("$form.name", FlowExpression.Scope.FORM, List.of("name")));
        assertThat(FlowExpression.parse("$route.vmId")).contains(
                new FlowExpression("$route.vmId", FlowExpression.Scope.ROUTE, List.of("vmId")));
        assertThat(FlowExpression.parse("$context.vmId")).contains(
                new FlowExpression("$context.vmId", FlowExpression.Scope.CONTEXT, List.of("vmId")));
        assertThat(FlowExpression.parse("$steps.create.response.data.id")).contains(
                new FlowExpression("$steps.create.response.data.id", FlowExpression.Scope.STEPS,
                        List.of("create", "response", "data", "id")));
        assertThat(FlowExpression.parse("$currentUser.id")).contains(
                new FlowExpression("$currentUser.id", FlowExpression.Scope.CURRENT_USER, List.of("id")));
    }

    @Test
    void parsesScopeAloneWithoutPath() {
        assertThat(FlowExpression.parse("$context")).contains(
                new FlowExpression("$context", FlowExpression.Scope.CONTEXT, List.of()));
    }

    @Test
    void rejectsArbitraryJavaScriptSyntax() {
        assertThat(FlowExpression.parse("$form.name.toUpperCase()")).isEmpty();
        assertThat(FlowExpression.parse("$context.vmId + 1")).isEmpty();
        assertThat(FlowExpression.parse("${form.name}")).isEmpty();
        assertThat(FlowExpression.parse("$form['name']")).isEmpty();
        assertThat(FlowExpression.parse("form.name")).isEmpty();
        assertThat(FlowExpression.parse("$unknownScope.name")).isEmpty();
        assertThat(FlowExpression.parse("plain literal text")).isEmpty();
    }

    @Test
    void isExpressionLikeOnlyMatchesDollarPrefixedStrings() {
        assertThat(FlowExpression.isExpressionLike("$context.vmId")).isTrue();
        assertThat(FlowExpression.isExpressionLike("literal value")).isFalse();
        assertThat(FlowExpression.isExpressionLike(null)).isFalse();
    }
}
