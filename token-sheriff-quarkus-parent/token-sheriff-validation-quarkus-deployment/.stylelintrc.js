/**
 * Stylelint configuration for CSS-in-JS in Lit components
 * 
 * This configuration ensures consistent CSS styling within
 * Lit component template literals and CSS-in-JS constructs.
 * Follows CUI CSS standards for modern CSS development.
 */

export default {
  extends: [
    'stylelint-config-standard'
  ],
  
  plugins: [
    'stylelint-order'
  ],
  
  // Custom syntax for CSS-in-JS
  customSyntax: 'postcss-lit',
  
  rules: {
    // Modern CSS formatting
    'color-hex-length': 'short',
    
    // Logical property ordering (not alphabetical)
    'order/properties-order': [
      'content',
      'display',
      'position',
      'top',
      'right',
      'bottom',
      'left',
      'z-index',
      'flex',
      'flex-grow',
      'flex-shrink',
      'flex-basis',
      'flex-direction',
      'flex-wrap',
      'justify-content',
      'align-items',
      'align-content',
      'align-self',
      'grid',
      'grid-template',
      'grid-template-rows',
      'grid-template-columns',
      'grid-template-areas',
      'grid-auto-rows',
      'grid-auto-columns',
      'grid-auto-flow',
      'grid-gap',
      'gap',
      'width',
      'min-width',
      'max-width',
      'height',
      'min-height',
      'max-height',
      'margin',
      'margin-top',
      'margin-right',
      'margin-bottom',
      'margin-left',
      'padding',
      'padding-top',
      'padding-right',
      'padding-bottom',
      'padding-left',
      'border',
      'border-radius',
      'background',
      'background-color',
      'color',
      'font',
      'font-family',
      'font-size',
      'font-weight',
      'line-height',
      'text-align',
      'opacity',
      'transform',
      'transition',
      'animation'
    ],
    
    // CSS Custom Properties patterns
    'custom-property-pattern': '^[a-z][a-z0-9]*(-[a-z0-9]+)*$',
    'custom-property-empty-line-before': 'never',
    
    // Modern selector patterns for Lit components
    'selector-class-pattern': '^[a-z][a-z0-9]*(-[a-z0-9]+)*(__[a-z0-9]+(-[a-z0-9]+)*)?(--[a-z0-9]+(-[a-z0-9]+)*)?$',
    'selector-id-pattern': '^[a-z][a-z0-9]*(-[a-z0-9]+)*$',
    'selector-max-compound-selectors': 4,
    'selector-max-specificity': '0,4,0',
    
    // Box model and layout
    'declaration-property-unit-allowed-list': {
      'font-size': ['rem', 'em', 'px'],
      'line-height': ['rem', 'em', 'px', ''],
      'margin': ['rem', 'em', 'px', '%'],
      'padding': ['rem', 'em', 'px', '%'],
      'width': ['rem', 'em', 'px', '%', 'vw', 'vh'],
      'height': ['rem', 'em', 'px', '%', 'vw', 'vh'],
    },
    
    // Color and theming - enforce CSS custom properties for color values
    'color-named': 'never',
    'color-no-hex': null,
    'declaration-property-value-allowed-list': {
      'color': ['/^var\\(--/', 'currentColor', 'inherit', 'initial', 'unset', 'transparent'],
      'background-color': ['/^var\\(--/', 'currentColor', 'inherit', 'initial', 'unset', 'transparent'],
      'border-color': ['/^var\\(--/', 'currentColor', 'inherit', 'initial', 'unset', 'transparent'],
      'fill': ['/^var\\(--/', 'currentColor', 'inherit', 'initial', 'unset', 'transparent'],
      'stroke': ['/^var\\(--/', 'currentColor', 'inherit', 'initial', 'unset', 'transparent'],
    },
    
    // CSS Grid and Flexbox
    'property-no-vendor-prefix': [
      true,
      {
        ignoreProperties: ['appearance', 'mask'],
      },
    ],
    
    // Lit-specific CSS patterns
    'selector-pseudo-class-no-unknown': [
      true,
      {
        ignorePseudoClasses: ['host', 'host-context', 'focus-visible'],
      },
    ],
    
    // Modern CSS features
    'at-rule-no-unknown': [true, {
      'ignoreAtRules': [
        'supports',
        'layer', 
        'container',
        'property'
      ]
    }],
    
    // Performance and maintainability
    'max-nesting-depth': 3,
    'selector-max-id': 0,
    'selector-max-universal': 1,
    'declaration-block-single-line-max-declarations': 1,
    
    // CSS quality rules
    'declaration-property-value-no-unknown': true,
    'function-no-unknown': true,
    'media-feature-name-no-unknown': true,
    'selector-pseudo-element-no-unknown': true,
    'selector-type-no-unknown': true,
    
    // Performance and best practices
    'no-duplicate-selectors': true,
    'no-descending-specificity': null, // Disabled for CSS-in-JS components
    'declaration-block-no-redundant-longhand-properties': true,
    
    
    // Disable rules that conflict with CSS-in-JS
    'no-empty-source': null,
    'value-keyword-case': null, // Allows CSS-in-JS template literals
  },
  
  overrides: [
    {
      files: ['src/main/resources/dev-ui/**/*.js'],
      rules: {
        // Stricter rules for production components
        'max-nesting-depth': 3,
        'selector-max-compound-selectors': 4,
        'selector-max-specificity': '0,4,0',
      },
    },
    {
      files: ['src/test/js/**/*.js'],
      rules: {
        // Relaxed rules for test files
        'selector-class-pattern': null,
        'custom-property-pattern': null,
        'max-nesting-depth': null,
      },
    },
  ],
  
  // Ignore patterns
  ignoreFiles: [
    'node_modules/**/*',
    'coverage/**/*',
    'dist/**/*',
    'build/**/*',
    '**/*.min.js',
  ],
};