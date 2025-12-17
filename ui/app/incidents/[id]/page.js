'use client';

import { useEffect, useState } from 'react';
import { fetchApi } from '../../../utils/api';
import { useParams } from 'next/navigation';
import { useRouter } from 'next/navigation';

// Helper component for displaying evidence with expand/collapse
const EvidenceDisplay = ({ evidence }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const renderSchemaDiff = (data) => {
    // Assuming data contains 'oldSchema' and 'newSchema' objects
    const oldSchema = data.oldSchema || {};
    const newSchema = data.newSchema || {};
    const changes = data.changes || []; // Array of change objects, e.g., { path: "/field", type: "removed" }

    return (
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <h4 className="font-semibold mb-2">Old Schema</h4>
          <pre className="bg-gray-100 p-2 rounded text-sm overflow-x-auto">
            {JSON.stringify(oldSchema, null, 2)}
          </pre>
        </div>
        <div>
          <h4 className="font-semibold mb-2">New Schema</h4>
          <pre className="bg-gray-100 p-2 rounded text-sm overflow-x-auto">
            {JSON.stringify(newSchema, null, 2)}
          </pre>
        </div>
        {changes.length > 0 && (
          <div className="md:col-span-2 mt-4">
            <h4 className="font-semibold mb-2">Detected Changes</h4>
            <ul className="list-disc pl-5 bg-gray-100 p-2 rounded text-sm">
              {changes.map((change, idx) => (
                <li key={idx}>
                  <strong>{change.type}:</strong> {change.path}
                  {change.oldValue && change.newValue && ` (from ${change.oldValue} to ${change.newValue})`}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    );
  };

  const renderMetricsSnapshot = (data) => {
    // Assuming data contains current metrics and baseline metrics
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <h4 className="font-semibold mb-2">Current Metrics (5min)</h4>
          <pre className="bg-gray-100 p-2 rounded text-sm overflow-x-auto">
            {JSON.stringify(data.currentMetrics, null, 2)}
          </pre>
        </div>
        <div>
          <h4 className="font-semibold mb-2">Baseline Metrics (24h)</h4>
          <pre className="bg-gray-100 p-2 rounded text-sm overflow-x-auto">
            {JSON.stringify(data.baselineMetrics, null, 2)}
          </pre>
        </div>
      </div>
    );
  };

  const renderEvidenceData = (data, type) => {
    switch (type) {
      case 'SCHEMA_DIFF':
        return renderSchemaDiff(data);
      case 'METRICS':
        return renderMetricsSnapshot(data);
      case 'SAMPLE_ERRORS': // Fallthrough for default JSON display
      case 'TIMELINE':
      default:
        return (
          <pre className="bg-gray-100 p-2 rounded text-sm overflow-x-auto">
            {JSON.stringify(data, null, 2)}
          </pre>
        );
    }
  };

  return (
    <div className="mb-4 p-3 border rounded-md">
      <div className="flex justify-between items-center cursor-pointer" onClick={() => setIsExpanded(!isExpanded)}>
        <p className="font-semibold">Evidence Type: {evidence.evidenceType} (Created: {new Date(evidence.createdAt).toLocaleString()})</p>
        <button className="text-blue-500 hover:text-blue-700">
          {isExpanded ? 'Collapse' : 'Expand'}
        </button>
      </div>
      {isExpanded && evidence.data && (
        <div className="mt-2">
          {renderEvidenceData(evidence.data, evidence.evidenceType)}
        </div>
      )}
      {!evidence.data && isExpanded && (
        <div className="mt-2 text-gray-500">No data available for this evidence.</div>
      )}
    </div>
  );
};

export default function IncidentDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const [incident, setIncident] = useState(null);
  const [evidence, setEvidence] = useState([]);
  const [rcaReport, setRcaReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadIncidentDetails = async () => {
    if (!id) return;
    try {
      const incidentData = await fetchApi(`/incidents/${id}`);
      setIncident(incidentData);

      const evidenceData = await fetchApi(`/incidents/${id}/evidence`);
      setEvidence(evidenceData);

      const rcaData = await fetchApi(`/incidents/${id}/rca`);
      setRcaReport(rcaData);

    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadIncidentDetails();
  }, [id]);

  const handleAcknowledge = async () => {
    try {
      await fetchApi(`/incidents/${id}/ack`, { method: 'POST' });
      await loadIncidentDetails(); // Reload data after action
    } catch (err) {
      setError(err.message);
    }
  };

  const handleResolve = async () => {
    try {
      await fetchApi(`/incidents/${id}/resolve`, { method: 'POST' });
      await loadIncidentDetails(); // Reload data after action
    } catch (err) {
      setError(err.message);
    }
  };

  if (loading) {
    return <div className="flex justify-center items-center h-screen text-lg">Loading incident details...</div>;
  }

  if (error) {
    return <div className="flex justify-center items-center h-screen text-lg text-red-600">Error: {error}</div>;
  }

  if (!incident) {
    return <div className="flex justify-center items-center h-screen text-lg">Incident not found.</div>;
  }

  return (
    <div className="container mx-auto p-4">
      <h1 className="text-3xl font-bold mb-6">Incident Details: {incident.id.substring(0, 8)}...</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        <div className="bg-white shadow rounded-lg p-4">
          <h2 className="text-xl font-semibold mb-4">Incident Overview</h2>
          <p><strong>Endpoint ID:</strong> {incident.endpointId}</p>
          <p><strong>Type:</strong> {incident.type}</p>
          <p><strong>Status:</strong> {incident.status}</p>
          <p><strong>Severity:</strong> {incident.severity}</p>
          <p><strong>Detected At:</strong> {new Date(incident.detectedAt).toLocaleString()}</p>
          {incident.acknowledgedAt && <p><strong>Acknowledged At:</strong> {new Date(incident.acknowledgedAt).toLocaleString()}</p>}
          {incident.resolvedAt && <p><strong>Resolved At:</strong> {new Date(incident.resolvedAt).toLocaleString()}</p>}
        </div>

        <div className="bg-white shadow rounded-lg p-4">
          <h2 className="text-xl font-semibold mb-4">Incident Timeline</h2>
          <ul className="list-disc pl-5">
            {incident.triggeredAt && <li>Triggered At: {new Date(incident.triggeredAt).toLocaleString()}</li>}
            {incident.detectedAt && <li>Detected At: {new Date(incident.detectedAt).toLocaleString()}</li>}
            {incident.acknowledgedAt && <li>Acknowledged At: {new Date(incident.acknowledgedAt).toLocaleString()}</li>}
            {incident.resolvedAt && <li>Resolved At: {new Date(incident.resolvedAt).toLocaleString()}</li>}
          </ul>
        </div>

        <div className="bg-white shadow rounded-lg p-4">
          <h2 className="text-xl font-semibold mb-4">RCA Report</h2>
          {rcaReport ? (
            <div>
              <p><strong>Status:</strong> {rcaReport.status}</p>
              <p><strong>Summary:</strong> {rcaReport.rootCauseSummary}</p>
              <p><strong>Likely Trigger:</strong> {rcaReport.likelyTrigger}</p>
              <p><strong>Confidence:</strong> {rcaReport.confidence}</p>
              {rcaReport.affectedEndpoints && rcaReport.affectedEndpoints.length > 0 && (
                  <p><strong>Affected Endpoints:</strong> {rcaReport.affectedEndpoints.join(', ')}</p>
              )}
              <p><strong>Severity Reason:</strong> {rcaReport.severityReason}</p>
              <p><strong>Rollback vs Patch:</strong> {rcaReport.rollbackVsPatchRecommendation}</p>
              {rcaReport.recommendedFixes && rcaReport.recommendedFixes.length > 0 && (
                <div>
                  <strong>Recommended Fixes:</strong>
                  <ul className="list-disc pl-5">
                    {rcaReport.recommendedFixes.map((fix, index) => <li key={index}>{fix}</li>)}
                  </ul>
                </div>
              )}
            </div>
          ) : (
            <p>No RCA report available.</p>
          )}
        </div>
      </div>

      <div className="bg-white shadow rounded-lg p-4 mb-6">
        <h2 className="text-xl font-semibold mb-4">Evidence</h2>
        {evidence.length > 0 ? (
          evidence.map((ev) => (
            <EvidenceDisplay key={ev.id} evidence={ev} />
          ))
        ) : (
          <p>No evidence available.</p>
        )}
      </div>

      <div className="flex space-x-4">
        {incident.status === 'OPEN' && (
            <button
                onClick={handleAcknowledge}
                className="bg-yellow-500 hover:bg-yellow-600 text-white font-bold py-2 px-4 rounded"
            >
              Acknowledge
            </button>
        )}
        {(incident.status === 'OPEN' || incident.status === 'ACKNOWLEDGED') && (
            <button
                onClick={handleResolve}
                className="bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded"
            >
              Resolve
            </button>
        )}
      </div>
    </div>
  );
}