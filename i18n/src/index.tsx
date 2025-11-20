#!/usr/bin/env node

import React from 'react';
import { render } from 'ink';
import App from './tui/App';
import { loadConfig } from './config';
import { configDotenv } from 'dotenv';

// Load environment variables
configDotenv();

const config = loadConfig('config.yml');

render(<App config={config} />);
