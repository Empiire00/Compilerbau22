package compiler;
import compiler.ast.*;

import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Parser {
    private Lexer m_lexer;
    private CompileEnv m_compileEnv;
    private SymbolTable m_symbolTable;

    public Parser(CompileEnv compileEnv, Lexer lexer) {
        m_compileEnv = compileEnv;
        m_lexer = lexer;
        m_symbolTable = m_compileEnv.getSymbolTable();
    }

    public SymbolTable getSymbolTable() {
        return m_symbolTable;
    }

    public ASTExprNode parseExpression(String val) throws Exception {
        m_lexer.init(val);
        return getExpr();
    }

    public ASTStmtNode parseStmt(String val) throws Exception {
        m_lexer.init(val);
        return getStmt();
    }

    ASTExprNode getExpr() throws Exception {
        return getQuestionMarkExpr();
    }

    ASTExprNode getParantheseExpr() throws Exception {
        ASTExprNode result = null;
        Token curToken = m_lexer.lookAhead();
        if (curToken.m_type.equals(Token.Type.LPAREN)) {
            m_lexer.expect(Token.Type.LPAREN);
            result = getExpr();
            m_lexer.expect(Token.Type.RPAREN);
            return new ASTParentheseExprNode(result);
        } else if (curToken.m_type.equals(Token.Type.INTEGER)) {
            m_lexer.advance();
            return new ASTIntegerLiteralNode(curToken.m_value);
        } else {
            return getVariableExpr();
        }
    }

    // unaryexpr: (NOT | MINUS) ? paranthesisexpr
    ASTExprNode getUnaryExpr() throws Exception {
        var token = m_lexer.lookAhead().m_type;

        if (token == TokenIntf.Type.MINUS || token == TokenIntf.Type.NOT) {
            m_lexer.advance();
        }

        var parenExpr = getParantheseExpr();
        return new ASTUnaryExprNode(parenExpr, token);
    }

    ASTExprNode getMulDivExpr() throws Exception {
        ASTExprNode result = getUnaryExpr();
        Token nextToken = m_lexer.lookAhead();
        while (nextToken.m_type == Token.Type.MUL || nextToken.m_type == Token.Type.DIV) {
            m_lexer.advance();
            result = new ASTMulDivExprNode(result, getUnaryExpr(), nextToken.m_type);
            nextToken = m_lexer.lookAhead();
        }
        return result;
    }

    ASTExprNode getPlusMinusExpr() throws Exception {
        ASTExprNode result = getMulDivExpr();
        Token nextToken = m_lexer.lookAhead();
        while (nextToken.m_type == Token.Type.PLUS || nextToken.m_type == Token.Type.MINUS) {
            m_lexer.advance();
            result = new ASTPlusMinusExprNode(result, getMulDivExpr(), nextToken.m_type);
            nextToken = m_lexer.lookAhead();
        }
        return result;
    }

    ASTExprNode getBitAndOrExpr() throws Exception {
        ASTExprNode result = getPlusMinusExpr();
        Token nextToken = m_lexer.lookAhead();
        while (nextToken.m_type == Token.Type.BITAND || nextToken.m_type == Token.Type.BITOR) {
            if (nextToken.m_type == Token.Type.BITAND) {
                m_lexer.advance();
                result = new ASTBitAndOrExprNode(result, getPlusMinusExpr(), Token.Type.BITAND);
            } else {
                m_lexer.advance();
                result = new ASTBitAndOrExprNode(result, getPlusMinusExpr(), Token.Type.BITOR);
            }
            nextToken = m_lexer.lookAhead();
        }
        return result;
    }

    ASTExprNode getShiftExpr() throws Exception {
        return getBitAndOrExpr();
    }

    ASTExprNode getCompareExpr() throws Exception {
        ASTExprNode result = getShiftExpr();
        Token nextToken = m_lexer.lookAhead();
        while (nextToken.m_type == Token.Type.LESS || nextToken.m_type == Token.Type.EQUAL || nextToken.m_type == Token.Type.GREATER) {
            m_lexer.advance();
            result = new ASTCompareExprNode(result, getShiftExpr(), nextToken.m_type);
            nextToken = m_lexer.lookAhead();
        }
        return result;
    }

    ASTExprNode getAndOrExpr() throws Exception {
        ASTExprNode result = getCompareExpr();
        Token nextToken = m_lexer.lookAhead();
        while (nextToken.m_type == Token.Type.AND || nextToken.m_type == Token.Type.OR) {
            m_lexer.advance();
            result = new ASTAndOrExprNode(result, getCompareExpr(), nextToken.m_type);
            nextToken = m_lexer.lookAhead();
        }
        return result;
    }

    ASTExprNode getQuestionMarkExpr() throws Exception {

        ASTExprNode toResolve = getAndOrExpr();
        while (m_lexer.lookAhead().m_type == Token.Type.QUESTIONMARK) {
          m_lexer.expect(Token.Type.QUESTIONMARK);
          ASTExprNode trueNum = getAndOrExpr();
          m_lexer.expect(Token.Type.DOUBLECOLON);
          ASTExprNode falseNum = getAndOrExpr();
          toResolve = new ASTQuestionmarkExprNode(toResolve, trueNum, falseNum);
        }
        return toResolve;
    }

    // blockstmt: LBRACE stmtlist RBRACE
    // stmtlist: stmt stmtlist
    // stmtlist: epsilon
    ASTStmtNode getBlockStmt() throws Exception {
        ASTBlockStmtNode result = new ASTBlockStmtNode();
        m_lexer.expect(Token.Type.LBRACE);
        while (m_lexer.lookAhead().m_type != Token.Type.RBRACE) {
            result.addStatement(getStmt());
        }
        m_lexer.expect(Token.Type.RBRACE);

        return result;
    }

    // stmt: declareStmt
    // stmt: assignStmt
    // stmt: printStmt
    // stmt: declareStmt
    // stmt: assignStmt
    // stmt: printStmt
    ASTStmtNode getStmt() throws Exception {
        Token token = m_lexer.lookAhead();
        if (token.m_type == Token.Type.DECLARE) {
            return getDeclareStmt();
        } else if (token.m_type == Token.Type.IDENT) {
            return getAssignStmt();
        } else if (token.m_type == Token.Type.PRINT) {
            return getPrintStmt();
        } else if (token.m_type == Token.Type.LBRACE) {
            return getBlockStmt();
        } else if(token.m_type == Token.Type.FUNCTION) {
          return getFuncDefStmt();
        }
        throw new Exception("Unexpected Statement");
    }

    // declareStmt: DECLARE IDENTIFIER SEMICOLON
    ASTStmtNode getDeclareStmt() throws Exception {
        m_lexer.expect(TokenIntf.Type.DECLARE);

        Token identifier = m_lexer.lookAhead();
        m_lexer.expect(TokenIntf.Type.IDENT);

        m_lexer.expect(TokenIntf.Type.SEMICOLON);

        if(m_symbolTable.getSymbol(identifier.m_value) != null) {
            throw new Exception("Das Symbol \"" + identifier.m_value + "\" ist bereits vergeben!\n");
        }

        m_symbolTable.createSymbol(identifier.m_value);

        return new ASTDeclareNode(m_symbolTable, identifier.m_value);
    }

    // assignStmt: IDENTIFER ASSIGN expr SEMICOLON
    ASTStmtNode getAssignStmt() throws Exception {
        Token nextToken = m_lexer.lookAhead();
        if(m_symbolTable.getSymbol(nextToken.m_value) == null) {
             throw new Exception("Die Variable \"" + nextToken.m_value + "\" ist noch nicht deklariert worden!\n");
         }
        m_lexer.expect(Token.Type.IDENT);
        m_lexer.expect(TokenIntf.Type.ASSIGN);
        ASTStmtNode stmtNode = new ASTAssignStmtNode(getExpr(), m_symbolTable.getSymbol(nextToken.m_value));
        m_lexer.expect(TokenIntf.Type.SEMICOLON);
        return stmtNode;
    }

    // printStmt: PRINT expr SEMICOLON
    ASTStmtNode getPrintStmt() throws Exception {
        m_lexer.expect(TokenIntf.Type.PRINT);
        var node = getExpr();
        m_lexer.expect(TokenIntf.Type.SEMICOLON);
        return new ASTPrintStmtNode(node);
    }

    // variableExpr: IDENTIFIER
    ASTExprNode getVariableExpr() throws Exception {
        Token token = m_lexer.lookAhead();
        if (token.m_type == Token.Type.IDENT){
            m_lexer.advance();
            return new ASTVariableExprNode(token.m_value, getSymbolTable());
        }
        throw new Exception("Unexpected Statement");

    }


    // func: FUNCTION IDENTIFIER LPAREN paramList RPAREN blockstmt
    ASTStmtNode getFuncDefStmt() throws Exception {
        m_lexer.expect(Token.Type.FUNCTION);
        Token name = m_lexer.lookAhead();
        m_lexer.advance();
        m_lexer.expect(Token.Type.LPAREN);
        List<ASTVariableExprNode> paramNodes = getFuncParamList();
        List<Symbol> paramSymbols = paramNodes.stream().map(param -> m_symbolTable.getSymbol(param.identifier)).collect(
            Collectors.toList());
        m_lexer.expect(Token.Type.RPAREN);

        // read function body
        ASTBlockStmtNode blockStmtExpr = (ASTBlockStmtNode) getBlockStmt();

        return new ASTFuncDefStmtNode(name.m_value, paramSymbols, blockStmtExpr);
    }

    // paramList: EPSILON
    // paramList: IDENTIFIER moreParams
    // moreParams: COMMA IDENTIFIER moreParams
    // moreParams: EPSILON
    private List<ASTVariableExprNode> getFuncParamList() throws Exception {
        List<ASTVariableExprNode> result = new ArrayList<>();

        Token.Type prevType = Token.Type.COMMA;
        while (m_lexer.lookAhead().m_type != Token.Type.RPAREN) {
            Token token = m_lexer.lookAhead();
            if(token.m_type == Token.Type.IDENT) {
                if(prevType != Token.Type.COMMA) {
                    throw new Exception("Unexpected identifier");
                }

                ASTVariableExprNode paramNode = (ASTVariableExprNode) getVariableExpr();
                m_symbolTable.createSymbol(paramNode.identifier);
                result.add(paramNode);
            } else if(token.m_type == Token.Type.COMMA) {
                if(prevType != Token.Type.IDENT) {
                    throw new Exception("Unexpected comma");
                }

                m_lexer.advance();
            }

            prevType = token.m_type;
        }

        return result;
    }


}