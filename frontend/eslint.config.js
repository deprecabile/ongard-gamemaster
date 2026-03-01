import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import checkFile from 'eslint-plugin-check-file';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import prettierPlugin from 'eslint-plugin-prettier';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'eslint.config.js', '**/*.d.ts'] },

  // Base JS rules (applied to all files in scope)
  js.configs.recommended,

  // TypeScript strict + stylistic type-checked (scoped to TS files)
  {
    files: ['**/*.{ts,tsx}'],
    extends: [...tseslint.configs.strictTypeChecked, ...tseslint.configs.stylisticTypeChecked],
    languageOptions: {
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    settings: { react: { version: 'detect' } },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'jsx-a11y': jsxA11y,
      'check-file': checkFile,
      'simple-import-sort': simpleImportSort,
      prettier: prettierPlugin,
    },
    rules: {
      // React
      ...react.configs.recommended.rules,
      ...react.configs['jsx-runtime'].rules,
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.configs.strict.rules,

      // Prettier
      'prettier/prettier': 'error',

      // TypeScript makes prop-types redundant
      'react/prop-types': 'off',

      // File & folder naming
      'check-file/filename-naming-convention': [
        'error',
        {
          'src/**/!(main).tsx': 'PASCAL_CASE',
          'src/**/*.ts': 'CAMEL_CASE',
        },
        { ignoreMiddleExtensions: true },
      ],
      'check-file/folder-naming-convention': [
        'error',
        {
          'src/**/': 'KEBAB_CASE',
        },
      ],

      // Import sorting
      'simple-import-sort/imports': [
        'error',
        {
          groups: [
            // 1. React first, then external libraries
            ['^react', '^@?\\w'],
            // 2. Project absolute imports (alias @/)
            ['^@/'],
            // 3. Style files (.scss) last
            ['^.+\\.s?css$'],
          ],
        },
      ],
      'simple-import-sort/exports': 'error',

      // Import hygiene
      'no-duplicate-imports': 'error',
      'no-restricted-imports': [
        'error',
        {
          patterns: ['../**/'],
        },
      ],

      // Code quality
      'no-console': ['warn', { allow: ['warn', 'error'] }],

      // React Refresh
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },

  // Prettier — must come last to override formatting rules
  prettier,
);
