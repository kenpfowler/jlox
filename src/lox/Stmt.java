package lox;

abstract class Stmt {
    interface Visitor<R> {
        R visitExpressionStmt(Expression stmt);

        R visitPrintStmt(Print stmt);

        R visitVarStmt(Var stmt);
    }

    static class Expression extends Stmt {
        Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        <R> void accept(Visitor<R> visitor) {
            visitor.visitExpressionStmt(this);
        }

        final Expr expression;
    }

    static class Print extends Stmt {
        Print(Expr expression) {
            this.expression = expression;
        }

        @Override
        <R> void accept(Visitor<R> visitor) {
            visitor.visitPrintStmt(this);
        }

        final Expr expression;
    }

    static class Var extends Stmt {
        Var(Token name, Expr initializer) {
            this.name = name;
            this.initializer = initializer;
        }

        @Override
        <R> void accept(Visitor<R> visitor) {
            visitor.visitVarStmt(this);
        }

        final Token name;
        final Expr initializer;
    }

    abstract <R> void accept(Visitor<R> visitor);
}
