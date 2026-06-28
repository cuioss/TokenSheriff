import { html, css, LitElement } from 'lit';
import { JsonRpc } from 'jsonrpc';

export class QwcJwtDebugger extends LitElement {
  jsonRpc = new JsonRpc('TokenSheriffDevUI');
  static styles = css`
    .debugger-container {
      max-width: 1200px;
      padding: 1rem;
    }

    .input-section {
      margin-bottom: 2rem;
    }

    .input-group {
      margin-bottom: 1rem;
    }

    .input-label {
      display: block;
      margin-bottom: 0.5rem;
      color: var(--lumo-primary-text-color);
      font-weight: 500;
    }

    .token-input {
      width: 100%;
      min-height: 120px;
      padding: 0.75rem;
      border: 1px solid var(--lumo-contrast-20pct);
      border-radius: 6px;
      background-color: var(--lumo-base-color);
      color: var(--lumo-primary-text-color);
      font-family: Monaco, Menlo, 'Ubuntu Mono', monospace;
      font-size: 0.875rem;
      resize: vertical;
    }

    .token-input:focus {
      border-color: var(--lumo-primary-color);
      box-shadow: 0 0 0 2px var(--lumo-primary-color-10pct);
      outline: none;
    }

    .button-group {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1rem;
    }

    .action-button {
      padding: 0.75rem 1.5rem;
      border: none;
      border-radius: 6px;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
    }

    .validate-button {
      background-color: var(--lumo-primary-color);
      color: var(--lumo-primary-contrast-color);
    }

    .validate-button:hover {
      background-color: var(--lumo-primary-color-50pct);
    }

    .validate-button:disabled {
      background-color: var(--lumo-contrast-20pct);
      color: var(--lumo-disabled-text-color);
      cursor: not-allowed;
    }

    .clear-button {
      border: 1px solid var(--lumo-contrast-20pct);
      background-color: var(--lumo-contrast-10pct);
      color: var(--lumo-primary-text-color);
    }

    .clear-button:hover {
      background-color: var(--lumo-contrast-20pct);
    }

    .sample-button {
      background-color: var(--lumo-success-color);
      color: var(--lumo-success-contrast-color);
    }

    .sample-button:hover {
      background-color: var(--lumo-success-color-50pct);
    }

    .results-section {
      margin-top: 2rem;
    }

    .result-card {
      margin-bottom: 1rem;
      padding: 1rem;
      border: 1px solid var(--lumo-contrast-10pct);
      border-radius: 8px;
    }

    .result-success {
      background-color: var(--lumo-success-color-10pct);
      border-color: var(--lumo-success-color-50pct);
    }

    .result-error {
      background-color: var(--lumo-error-color-10pct);
      border-color: var(--lumo-error-color-50pct);
    }

    .result-header {
      display: flex;
      align-items: center;
      margin-bottom: 1rem;
    }

    .result-icon {
      width: 20px;
      height: 20px;
      margin-right: 0.5rem;
      border-radius: 50%;
    }

    .icon-success {
      background-color: var(--lumo-success-color);
    }

    .icon-error {
      background-color: var(--lumo-error-color);
    }

    .result-title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 600;
    }

    .result-title-success {
      color: var(--lumo-success-text-color);
    }

    .result-title-error {
      color: var(--lumo-error-text-color);
    }

    .claims-section {
      margin-top: 1rem;
    }

    .claims-title {
      margin-bottom: 0.5rem;
      font-weight: 600;
    }

    .claims-container {
      padding: 1rem;
      border: 1px solid var(--lumo-contrast-10pct);
      border-radius: 6px;
      background-color: var(--lumo-contrast-5pct);
    }

    .claims-json {
      margin: 0;
      color: var(--lumo-primary-text-color);
      font-family: Monaco, Menlo, 'Ubuntu Mono', monospace;
      font-size: 0.875rem;
      white-space: pre-wrap;
      word-break: break-all;
    }

    .error-message {
      color: var(--lumo-error-text-color);
      font-weight: 500;
    }

    .token-info {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-bottom: 1rem;
    }

    .info-item {
      padding: 0.75rem;
      border: 1px solid var(--lumo-contrast-10pct);
      border-radius: 6px;
      background-color: var(--lumo-contrast-5pct);
    }

    .info-label {
      margin-bottom: 0.25rem;
      color: var(--lumo-secondary-text-color);
      font-size: 0.875rem;
    }

    .info-value {
      color: var(--lumo-primary-text-color);
      font-weight: 600;
    }

    .loading {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: var(--lumo-secondary-text-color);
    }

    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid var(--lumo-contrast-20pct);
      border-radius: 50%;
      animation: spin 1s linear infinite;
      border-top: 2px solid var(--lumo-primary-color);
    }

    @keyframes spin {
      0% {
        transform: rotate(0deg);
      }

      100% {
        transform: rotate(360deg);
      }
    }
  `;

  static properties = {
    _token: { state: true },
    _validationResult: { state: true },
    _validating: { state: true },
  };

  constructor() {
    super();
    this._token = '';
    this._validationResult = null;
    this._validating = false;
  }

  _handleTokenInput(e) {
    this._token = e.target.value;
  }

  _clearToken() {
    this._token = '';
    this._validationResult = null;
    this.shadowRoot.querySelector('.token-input').value = '';
  }

  _loadSampleToken() {
    // Sample JWT token for testing (this would be generated by the backend in a real scenario)
    const sampleToken = [
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9',
      'eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ',
      'SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c',
    ].join('.');
    this._token = sampleToken;
    this.shadowRoot.querySelector('.token-input').value = sampleToken;
  }

  async _validateToken() {
    if (!this._token || this._token.trim().length === 0) {
      this._validationResult = {
        valid: false,
        error: 'Please enter a JWT token',
      };
      return;
    }

    try {
      this._validating = true;
      this._validationResult = null;

      const jsonRpcResponse = await this.jsonRpc.validateToken({ token: this._token.trim() });
      this._validationResult = jsonRpcResponse.result;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Error validating token:', error);
      this._validationResult = {
        valid: false,
        error: `Failed to validate token: ${error.message}`,
      };
    } finally {
      this._validating = false;
    }
  }

  _formatJson(obj) {
    return JSON.stringify(obj, null, 2);
  }

  _getResultCardClass() {
    if (this._validationResult.valid) {
      return 'result-success';
    }
    return 'result-error';
  }

  _getResultIconClass() {
    if (this._validationResult.valid) {
      return 'icon-success';
    }
    return 'icon-error';
  }

  _getResultTitleClass() {
    if (this._validationResult.valid) {
      return 'result-title-success';
    }
    return 'result-title-error';
  }

  _getResultTitleText() {
    if (this._validationResult.valid) {
      return 'Token is Valid';
    }
    return 'Token is Invalid';
  }

  _renderValidationContent() {
    if (this._validationResult.valid) {
      const claimsContent = this._validationResult.claims
        ? html`
            <div class="claims-section" data-testid="jwt-debugger-claims">
              <div class="claims-title">Token Claims</div>
              <div class="claims-container">
                <pre class="claims-json">${this._formatJson(this._validationResult.claims)}</pre>
              </div>
            </div>
          `
        : '';

      return html`
        <div class="token-info">
          <div class="info-item">
            <div class="info-label">Token Type</div>
            <div class="info-value">${this._validationResult.tokenType || 'Unknown'}</div>
          </div>
        </div>
        ${claimsContent}
      `;
    }
    const detailsContent = this._validationResult.details
      ? html`
          <div style="margin-top: 1rem; color: var(--lumo-secondary-text-color); font-size: 0.875rem;">
            ${this._validationResult.details}
          </div>
        `
      : '';

    return html`
      <div class="error-message">${this._validationResult.error || 'Token validation failed'}</div>
      ${detailsContent}
    `;
  }

  _renderValidateButtonContent() {
    if (this._validating) {
      return html`
        <span class="loading">
          <div class="spinner"></div>
          Validating...
        </span>
      `;
    }
    return 'Validate Token';
  }

  _renderResultsSection() {
    if (!this._validationResult) {
      return '';
    }

    return html`
      <div class="results-section" data-testid="jwt-debugger-result">
        <div class="result-card ${this._getResultCardClass()}">
          <div class="result-header">
            <div class="result-icon ${this._getResultIconClass()}"></div>
            <h4 class="result-title ${this._getResultTitleClass()}" data-testid="jwt-debugger-result-title">
              ${this._getResultTitleText()}
            </h4>
          </div>

          ${this._renderValidationContent()}
        </div>
      </div>
    `;
  }

  render() {
    return html`
      <div class="debugger-container" data-testid="jwt-debugger-container">
        <div class="input-section">
          <div class="input-group">
            <label class="input-label">JWT Token</label>
            <textarea
              class="token-input"
              data-testid="jwt-debugger-token-input"
              placeholder="Paste your JWT token here..."
              @input="${this._handleTokenInput}"
            ></textarea>
          </div>

          <div class="button-group">
            <button
              class="action-button validate-button"
              data-testid="jwt-debugger-validate-button"
              ?disabled="${this._validating}"
              @click="${this._validateToken}"
            >
              ${this._renderValidateButtonContent()}
            </button>

            <button
              class="action-button clear-button"
              data-testid="jwt-debugger-clear-button"
              @click="${this._clearToken}"
            >
              Clear
            </button>

            <button
              class="action-button sample-button"
              data-testid="jwt-debugger-sample-button"
              @click="${this._loadSampleToken}"
            >
              Load Sample
            </button>
          </div>
        </div>

        ${this._renderResultsSection()}
      </div>
    `;
  }
}

customElements.define('qwc-jwt-debugger', QwcJwtDebugger);
