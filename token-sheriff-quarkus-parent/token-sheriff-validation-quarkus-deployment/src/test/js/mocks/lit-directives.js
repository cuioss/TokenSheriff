/**
 * Mock implementation of Lit directives
 *
 * This mock provides the essential Lit directive functionality
 * needed for testing components that use directives like unsafeHTML.
 */

// Mock unsafeHTML directive
export const unsafeHTML = jest.fn(value => {
  // For testing purposes, just return the value as-is
  // In real Lit, this would sanitize and render HTML
  return value || '';
});

// Mock repeat directive
export const repeat = jest.fn((items, keyFn, template) => {
  if (!Array.isArray(items)) {
    return '';
  }

  return items
    .map((item, index) => {
      // Generate key for compatibility but don't use it in tests
      if (keyFn) keyFn(item, index);
      return template(item, index);
    })
    .join('');
});

// Mock when directive
export const when = jest.fn((condition, trueTemplate, falseTemplate) => {
  if (condition) {
    return trueTemplate();
  }

  if (falseTemplate) {
    return falseTemplate();
  }

  return '';
});

// Mock choose directive
export const choose = jest.fn((value, cases, defaultCase) => {
  for (const [caseValue, template] of cases) {
    if (value === caseValue) {
      return template();
    }
  }
  return defaultCase ? defaultCase() : '';
});

// Mock map directive
export const map = jest.fn((items, template) => {
  if (!Array.isArray(items)) {
    return '';
  }

  return items.map((item, index) => template(item, index)).join('');
});

// Mock join directive
export const join = jest.fn((items, joiner) => {
  if (!Array.isArray(items)) {
    return '';
  }

  return items.join(joiner || '');
});

// Mock range directive
export const range = jest.fn((startOrEnd, end, step = 1) => {
  const start = end === undefined ? 0 : startOrEnd;
  const actualEnd = end === undefined ? startOrEnd : end;

  const result = [];
  for (let i = start; i < actualEnd; i += step) {
    result.push(i);
  }
  return result;
});

// Mock until directive for async content
export const until = jest.fn((...values) => {
  // Return the last non-promise value or empty string
  for (let i = values.length - 1; i >= 0; i--) {
    const value = values[i];
    if (!(value instanceof Promise)) {
      return value;
    }
  }
  return '';
});

// Mock asyncAppend directive
export const asyncAppend = jest.fn((_asyncIterable, _mapper) => {
  // For testing, just return empty string
  // Real implementation would handle async iteration
  return '';
});

// Mock asyncReplace directive
export const asyncReplace = jest.fn((_asyncIterable, _mapper) => {
  // For testing, just return empty string
  // Real implementation would handle async iteration
  return '';
});

// Mock cache directive
export const cache = jest.fn(value => value);

// Mock keyed directive
export const keyed = jest.fn((key, value) => value);

// Mock guard directive
export const guard = jest.fn((dependencies, valueFn) => valueFn());

// Mock live directive
export const live = jest.fn(value => value);

// Mock ref directive
export const ref = jest.fn(_refOrCallback => {
  // Return a mock ref object
  return {
    value: null,
  };
});

// Mock classMap directive
export const classMap = jest.fn(classInfo => {
  if (!classInfo || typeof classInfo !== 'object') {
    return '';
  }

  return Object.entries(classInfo)
    .filter(([, value]) => value)
    .map(([className]) => className)
    .join(' ');
});

// Mock styleMap directive
export const styleMap = jest.fn(styleInfo => {
  if (!styleInfo || typeof styleInfo !== 'object') {
    return '';
  }

  return Object.entries(styleInfo)
    .map(([property, value]) => `${property}: ${value}`)
    .join('; ');
});

// Mock ifDefined directive
export const ifDefined = jest.fn(value => value ?? undefined);

// Helper function to reset all directive mocks
export const resetDirectiveMocks = () => {
  unsafeHTML.mockClear();
  repeat.mockClear();
  when.mockClear();
  choose.mockClear();
  map.mockClear();
  join.mockClear();
  range.mockClear();
  until.mockClear();
  asyncAppend.mockClear();
  asyncReplace.mockClear();
  cache.mockClear();
  keyed.mockClear();
  guard.mockClear();
  live.mockClear();
  ref.mockClear();
  classMap.mockClear();
  styleMap.mockClear();
  ifDefined.mockClear();
};

export default {
  unsafeHTML,
  repeat,
  when,
  choose,
  map,
  join,
  range,
  until,
  asyncAppend,
  asyncReplace,
  cache,
  keyed,
  guard,
  live,
  ref,
  classMap,
  styleMap,
  ifDefined,
  resetDirectiveMocks,
};
