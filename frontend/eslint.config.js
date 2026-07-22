import js from '@eslint/js'
import globals from 'globals'
import tsParser from '@typescript-eslint/parser'
import tsPlugin from '@typescript-eslint/eslint-plugin'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'

export default [
  { ignores: ['dist/**', 'node_modules/**'] },

  // 앱 소스 (브라우저 환경)
  {
    files: ['src/**/*.{ts,tsx}'],
    ...js.configs.recommended,
    languageOptions: {
      ecmaVersion: 2020,
      sourceType: 'module',
      globals: globals.browser,
      parser: tsParser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...js.configs.recommended.rules,
      ...tsPlugin.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // 타입 전용 식별자(React.PointerEvent, OscillatorType 등)를 미정의 변수로 오탐하므로
      // TS 파일에서는 끈다 — 미정의 변수 검사는 tsc가 담당 (typescript-eslint 공식 권장)
      'no-undef': 'off',
      // 미사용 변수는 tsc(noUnusedLocals)도 잡지만, _ 접두사는 의도적 무시로 허용
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
    },
  },

  // 빌드 설정 파일 (Node 환경)
  {
    files: ['*.config.{ts,js}'],
    languageOptions: {
      ecmaVersion: 2020,
      sourceType: 'module',
      globals: globals.node,
      parser: tsParser,
    },
    plugins: { '@typescript-eslint': tsPlugin },
    rules: { ...js.configs.recommended.rules },
  },
]
