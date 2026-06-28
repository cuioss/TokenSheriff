import js from '@eslint/js';
import jest from 'eslint-plugin-jest';
import unicorn from 'eslint-plugin-unicorn';
import prettier from 'eslint-plugin-prettier';
import lit from 'eslint-plugin-lit';
import wc from 'eslint-plugin-wc';

export default [
  js.configs.recommended,
  {
    plugins: {
      jest,
      unicorn,
      prettier,
      lit,
      wc,
    },
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        console: 'readonly',
        process: 'readonly',
        global: 'readonly',
        Buffer: 'readonly',
        __dirname: 'readonly',
        __filename: 'readonly',
        module: 'readonly',
        require: 'readonly',
        exports: 'readonly',
        document: 'readonly',
        window: 'readonly',
        HTMLElement: 'readonly',
        customElements: 'readonly',
        CSSStyleSheet: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        Headers: 'readonly',
        fetch: 'readonly',
        waitForComponentUpdate: 'readonly',
        navigator: 'readonly',
      },
    },
    rules: {
      // Core ESLint rules
      'no-console': 'warn',
      'no-debugger': 'error',
      'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'prefer-const': 'error',
      'no-var': 'error',
      
      // Code quality rules (manual implementation of key SonarJS patterns)
      'complexity': ['warn', { max: 15 }],
      'max-statements': ['warn', { max: 20 }],
      'max-params': ['warn', { max: 5 }],
      
      // Unicorn rules for modern JavaScript
      'unicorn/prefer-module': 'error',
      'unicorn/prefer-node-protocol': 'error',
      'unicorn/no-array-for-each': 'off', // Allow forEach for readability
      'unicorn/prevent-abbreviations': 'off', // Allow common abbreviations
      
      // Lit-specific rules
      'lit/no-invalid-html': 'error',
      'lit/no-legacy-template-syntax': 'error',
      'lit/no-property-change-update': 'error',
      
      // Web Components rules
      'wc/no-constructor-attributes': 'error',
      'wc/no-invalid-element-name': 'error',
      
      // Prettier integration
      'prettier/prettier': 'error',
    },
  },
  {
    files: ['**/*.test.js', '**/test/**/*.js'],
    plugins: {
      jest,
    },
    languageOptions: {
      globals: {
        jest: 'readonly',
        describe: 'readonly',
        it: 'readonly',
        test: 'readonly',
        expect: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly',
        beforeAll: 'readonly',
        afterAll: 'readonly',
      },
    },
    rules: {
      'jest/no-disabled-tests': 'warn',
      'jest/no-focused-tests': 'error',
      'jest/no-identical-title': 'error',
      'jest/prefer-to-have-length': 'warn',
      'jest/valid-expect': 'error',
    },
  },
];