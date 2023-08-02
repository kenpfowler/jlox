package lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(TokenType.VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(TokenType.EQUAL)) {
            initializer = expression();
        }

        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }


    private Stmt statement() {
        if (match(TokenType.FOR)) return forStmt();
        if (match(TokenType.IF)) return ifStmt();
        if (match(TokenType.PRINT)) return printStmt();
        if (match(TokenType.WHILE)) return whileStmt();
        if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStmt();
    }


    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = equality();

        if (match(TokenType.EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            throw error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    /**
     * NOTE: for loops are syntactic sugar that are built on top of while loops.
     * therefore, instead of returning a for statement here, we DESUGAR the input and return a while statement that represents the for loop.
     *
     * @return Stmt.While
     */
    private Stmt forStmt() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt initializer;
        if (match(TokenType.SEMICOLON)) {
            initializer = null;
        } else if (match(TokenType.VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStmt();
        }

        Expr condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = expression();
        }
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(TokenType.RIGHT_PAREN)) {
            increment = expression();
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();
        if (increment != null) {
            body = new Stmt.Block(
                    Arrays.asList(
                            body,
                            new Stmt.Expression(increment)));
        }

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt whileStmt() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");
        Stmt body = this.statement();

        return new Stmt.While(condition, body);
    }


    private Stmt ifStmt() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = statement();
        }


        return new Stmt.If(condition, thenBranch, elseBranch);
    }


    private Stmt printStmt() {
        Expr value = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }


    private Stmt expressionStmt() {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }


    private Expr equality() {
        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(TokenType.MINUS, TokenType.PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(TokenType.SLASH, TokenType.STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.TRUE)) return new Expr.Literal(true);
        if (match(TokenType.NIL)) return new Expr.Literal(null);

        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }


        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }


    /**
     * attempts to advance the parser if the token type/s match the current token
     *
     * @param types tokens
     * @return boolean
     */
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /**
     * eagerly parse the current token and throw an error if its unexpected
     *
     * @param type    token
     * @param message error message to be shown if parsing fails
     * @return token
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }


    /**
     * checks if the current token is of the provided type
     *
     * @param type token
     * @return boolean
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    /**
     * @return the current token
     */
    private Token peek() {
        return tokens.get(current);
    }

    /**
     * @return the previous token
     */
    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }


}
