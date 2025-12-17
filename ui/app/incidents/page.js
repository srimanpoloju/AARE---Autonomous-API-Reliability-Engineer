'use client';

import { useEffect, useState } from 'react';
import { fetchApi } from '../../utils/api';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';

const IncidentStatusEnum = ['OPEN', 'ACKNOWLEDGED', 'RESOLVED'];
const IncidentTypeEnum = ['ERROR_SPIKE', 'LATENCY_REGRESSION', 'CONTRACT_BREAK', 'TRAFFIC_DROP'];
const IncidentSeverityEnum = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export default function IncidentsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [statusFilter, setStatusFilter] = useState(searchParams.get('status') || '');
  const [typeFilter, setTypeFilter] = useState(searchParams.get('type') || '');
  // const [severityFilter, setSeverityFilter] = useState(searchParams.get('severity') || ''); // Backend doesn't support yet

  useEffect(() => {
    async function loadIncidents() {
      setLoading(true);
      setError('');
      try {
        const queryParams = new URLSearchParams();
        if (statusFilter) queryParams.append('status', statusFilter);
        if (typeFilter) queryParams.append('type', typeFilter);
        // if (severityFilter) queryParams.append('severity', severityFilter); // Backend doesn't support yet

        const queryString = queryParams.toString();
        const url = `/incidents${queryString ? `?${queryString}` : ''}`;
        
        const data = await fetchApi(url);
        setIncidents(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    loadIncidents();

    // Update URL query params
    const newSearchParams = new URLSearchParams();
    if (statusFilter) newSearchParams.set('status', statusFilter);
    if (typeFilter) newSearchParams.set('type', typeFilter);
    // if (severityFilter) newSearchParams.set('severity', severityFilter);
    router.push(`?${newSearchParams.toString()}`, undefined, { shallow: true });

  }, [statusFilter, typeFilter, router, searchParams]); // Add searchParams to dependencies

  const getSeverityBadge = (severity) => {
    let colorClass = '';
    switch (severity) {
      case 'LOW':
        colorClass = 'bg-blue-100 text-blue-800';
        break;
      case 'MEDIUM':
        colorClass = 'bg-yellow-100 text-yellow-800';
        break;
      case 'HIGH':
        colorClass = 'bg-orange-100 text-orange-800';
        break;
      case 'CRITICAL':
        colorClass = 'bg-red-100 text-red-800';
        break;
      default:
        colorClass = 'bg-gray-100 text-gray-800';
    }
    return <span className={\`px-2 inline-flex text-xs leading-5 font-semibold rounded-full \${colorClass}\`}>{severity}</span>;
  };

  if (loading) {
    return <div className="flex justify-center items-center h-screen text-lg">Loading incidents...</div>;
  }

  if (error) {
    return <div className="flex justify-center items-center h-screen text-lg text-red-600">Error: {error}</div>;
  }

  return (
    <div className="container mx-auto p-4">
      <h1 className="text-3xl font-bold mb-6">Incidents Dashboard</h1>

      <div className="mb-4 flex space-x-4">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="p-2 border rounded-md"
        >
          <option value="">All Statuses</option>
          {IncidentStatusEnum.map(status => (
            <option key={status} value={status}>{status}</option>
          ))}
        </select>

        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="p-2 border rounded-md"
        >
          <option value="">All Types</option>
          {IncidentTypeEnum.map(type => (
            <option key={type} value={type}>{type}</option>
          ))}
        </select>

        {/* Placeholder for Severity Filter if backend implemented */}
        {/*
        <select
          value={severityFilter}
          onChange={(e) => setSeverityFilter(e.target.value)}
          className="p-2 border rounded-md"
        >
          <option value="">All Severities</option>
          {IncidentSeverityEnum.map(severity => (
            <option key={severity} value={severity}>{severity}</option>
          ))}
        </select>
        */}
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full bg-white border border-gray-200">
          <thead>
            <tr>
              <th className="py-2 px-4 border-b text-left">ID</th>
              <th className="py-2 px-4 border-b text-left">Endpoint</th>
              <th className="py-2 px-4 border-b text-left">Type</th>
              <th className="py-2 px-4 border-b text-left">Status</th>
              <th className="py-2 px-4 border-b text-left">Severity</th>
              <th className="py-2 px-4 border-b text-left">Detected At</th>
              <th className="py-2 px-4 border-b text-left">Actions</th>
            </tr>
          </thead>
          <tbody>
            {incidents.map((incident) => (
              <tr key={incident.id} className="hover:bg-gray-50">
                <td className="py-2 px-4 border-b">{incident.id.substring(0, 8)}...</td>
                <td className="py-2 px-4 border-b">{incident.endpointId}</td>
                <td className="py-2 px-4 border-b">{incident.type}</td>
                <td className="py-2 px-4 border-b">{getSeverityBadge(incident.severity)}</td>
                <td className="py-2 px-4 border-b">{incident.status}</td>
                <td className="py-2 px-4 border-b">{new Date(incident.detectedAt).toLocaleString()}</td>
                <td className="py-2 px-4 border-b">
                  <Link href={`/incidents/${incident.id}`} className="text-blue-600 hover:underline">
                    View
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
